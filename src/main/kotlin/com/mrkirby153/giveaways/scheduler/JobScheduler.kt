package com.mrkirby153.giveaways.scheduler

import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.giveaways.scheduler.jpa.JobEntity
import com.mrkirby153.giveaways.scheduler.jpa.JobRepository
import com.mrkirby153.giveaways.scheduler.message.CancelJob
import com.mrkirby153.giveaways.scheduler.message.GlobalMessageService
import com.mrkirby153.giveaways.scheduler.message.JobScheduled
import com.mrkirby153.giveaways.scheduler.message.RescheduleJob
import com.mrkirby153.giveaways.utils.log
import com.rabbitmq.client.Channel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import me.mrkirby153.kcutils.Time
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import javax.transaction.Transactional


const val DEFAULT_JOB_QUEUE = "default"

const val JOB_QUEUE_FORMAT = "queue_%s"

interface JobScheduler {
    fun <T : Any> schedule(job: Job<T>, runAt: Timestamp, queue: String = DEFAULT_JOB_QUEUE): Long

    fun cancel(jobId: Long, broadcast: Boolean = true): Boolean

    fun listen(queue: String)

    fun unlisten(queue: String)

    fun reschedule(jobId: Long, newTime: Timestamp, brodcast: Boolean = true)
}

@Service
class JobManager(
    @Lazy
    private val globalMessageService: GlobalMessageService,
    private val repository: JobRepository,
    private val admin: AmqpAdmin,
    private val rabbitTemplate: RabbitTemplate,
    private val connectionFactory: ConnectionFactory,
    private val publisher: ApplicationEventPublisher,
    private val scheduler: TaskScheduler,
    private val applicationContext: ApplicationContext
) : JobScheduler {

    private val listeningQueues = mutableMapOf<String, SimpleMessageListenerContainer>()
    private val waitingJobs = ConcurrentHashMap<Long, Triple<ScheduledFuture<*>, Long, Channel>>()

    override fun <T : Any> schedule(
        job: Job<T>,
        runAt: Timestamp,
        queue: String
    ): Long {
        log.debug("Scheduling {} to run at {}", job, runAt)
        val serializedData = serializeJobData(job.data)
        val jobClass = job::class.java.canonicalName
        val entity = repository.save(JobEntity(jobClass, serializedData, queue, runAt))
        publish(queue, JobScheduled(entity.id!!, entity.runAt.time))
        return entity.id!!
    }

    @Transactional
    override fun cancel(jobId: Long, broadcast: Boolean): Boolean {
        val waiting = waitingJobs.remove(jobId)
        if (waiting != null) {
            log.debug("Canceling {}", jobId)
            waiting.first.cancel(false)
            waiting.third.basicAck(waiting.second, false)
        }
        if (broadcast) {
            log.debug("Broadcasting and removing job {}", jobId)
            repository.findById(jobId).ifPresent {
                globalMessageService.broadcast(CancelJob(it.id!!))
                repository.delete(it)
            }
        }
        return true
    }

    override fun listen(queue: String) {
        if (queue in listeningQueues.keys) {
            log.warn("Attempting to listen to a queue that we're already listening to: {}", queue)
            return
        }
        declareQueue(queue)
        log.debug("Started listening on queue $queue")
        val listenerContainer = SimpleMessageListenerContainer().apply {
            setMessageListener(JobQueueListener(publisher, this@JobManager))
            acknowledgeMode = AcknowledgeMode.MANUAL
            connectionFactory = this@JobManager.connectionFactory
            setPrefetchCount(65535)
            addQueueNames(JOB_QUEUE_FORMAT.format(queue))
            setAmqpAdmin(admin)
        }
        listeningQueues[queue] = listenerContainer
        listenerContainer.start()
    }

    override fun unlisten(queue: String) {
        if (queue !in listeningQueues.keys) {
            log.warn("Attempting to stop listening to a queue we're not listening to: {}", queue)
            return
        }
        val container = listeningQueues.remove(queue)
        container?.stop()
    }

    override fun reschedule(jobId: Long, newTime: Timestamp, broadcast: Boolean) {
        check(newTime.after(Timestamp.from(Instant.now()))) { "Cannot reschedule a task to a time in the past" }
        val existing = waitingJobs[jobId]

        if (existing != null && !broadcast) {
            log.debug("Rescheduling job {} to run at {}", jobId, newTime)
            val job = repository.findById(jobId)
            val (future, channel, deliveryTag) = existing
            if (!future.isDone && !future.isCancelled) {
                log.debug("Cancelling future and replacing")
                future.cancel(false)
                job.ifPresent {
                    val newFuture = scheduler.schedule({
                        invoke(it)
                    }, newTime)
                    waitingJobs[jobId] = Triple(newFuture, channel, deliveryTag)
                    it.runAt = newTime
                    repository.save(it)
                }
            }
        }
        if (broadcast) {
            log.debug("Broadcasting reschedule")
            globalMessageService.broadcast(RescheduleJob(jobId, newTime))
        }
    }

    private fun declareQueue(queue: String) {
        val ampqQueue = Queue(JOB_QUEUE_FORMAT.format(queue), true, false, false)
        admin.declareQueue(ampqQueue)
    }

    private fun deleteQueue(queue: String) {
        admin.deleteQueue(JOB_QUEUE_FORMAT.format(queue))
    }

    private inline fun <reified T : Any> publish(queue: String, message: T) {
        val serialized = Json.encodeToString(message)
        val type = message.javaClass.canonicalName
        val msg = Message(serialized.toByteArray())
        msg.messageProperties.setHeader("type", type)
        rabbitTemplate.send(JOB_QUEUE_FORMAT.format(queue), msg)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun getSerializer(data: Any) = serializer(data::class.java)

    private fun serializeJobData(data: Any): String {
        val jobData =
            JobData(data::class.java.canonicalName, Json.encodeToString(getSerializer(data), data))
        return Json.encodeToString(jobData)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun deserializeJobData(data: String): Any {
        val jobData = Json.decodeFromString<JobData>(data)
        val type = try {
            Class.forName(jobData.type)
        } catch (e: ClassNotFoundException) {
            error("Could not find class for job data ${jobData.type}")
        }
        return Json.decodeFromString(serializer(type), jobData.data)
    }

    private fun scheduleForExecution(id: Long, channel: Channel, deliveryTag: Long) {
        val optJob = repository.findById(id)
        if (!optJob.isPresent) {
            log.warn("Attempting to schedule {} for execution but it was not found", id)
            return
        }
        val job = optJob.get()
        val existing = waitingJobs[id]
        if (job.runAt.before(Timestamp.from(Instant.now()))) {
            if (existing == null) {
                log.debug(
                    "Executing job {} immediately. It should've ran {} ago",
                    id,
                    Time.format(1, System.currentTimeMillis() - job.runAt.time)
                )
                val future = scheduler.schedule({
                    invoke(job)
                }, Instant.now())
                waitingJobs[id] = Triple(future, deliveryTag, channel)
            }
            log.warn("Job {} is already executing, not executing again", id)
            return
        }
        if (existing != null) {
            log.debug("Rescheduling {}", id)
            if (!existing.first.isDone && !existing.first.isCancelled)
                existing.first.cancel(false)
            existing.third.basicAck(existing.second, false)
        }
        log.debug("Scheduling execution of job {} at {}", id, job.runAt)
        val future = scheduler.schedule({
            invoke(job)
        }, job.runAt)
        waitingJobs[id] = Triple(future, deliveryTag, channel)
    }

    private fun invoke(task: JobEntity) {
        log.debug("Executing job {}", task.id)
        try {
            val jobInstance = try {
                applicationContext.getBean(Class.forName(task.backingClass))
            } catch (e: Exception) {
                when (e) {
                    is ClassNotFoundException -> {
                        log.error(
                            "Unable to execute job {}, class not found {}",
                            task.id,
                            task.backingClass
                        )
                        return
                    }

                    is NoSuchBeanDefinitionException -> {
                        try {
                            Class.forName(task.backingClass).getConstructor().newInstance()
                        } catch (e: Exception) {
                            log.error(
                                "Unable to execute job {}, could not instantiate class {}",
                                task.id,
                                task.backingClass,
                                e
                            )
                            return
                        }
                    }

                    else -> throw e
                }
            }
            check(jobInstance is Job<*>) { "Class ${task.backingClass} does not inherit from Job" }
            if (task.data != null) {
                @Suppress("UNCHECKED_CAST")
                (jobInstance as Job<in Any>).data = deserializeJobData(task.data!!)
            }
            jobInstance.run()
        } catch (e: Exception) {
            log.error("An exception occurred processing job {}", task.id, e)
        } finally {
            log.debug("Removing job {} from waiting jobs", task.id)
            val storedData = waitingJobs.remove(task.id)
            if (storedData != null) {
                log.debug("Acking job {} with delivery tag {}", task.id, storedData.second)
                storedData.third.basicAck(storedData.second, false)
            }
            task.id?.let { repository.deleteById(it) }
        }
    }

    @EventListener
    fun onReady(event: BotReadyEvent) {
        listen(DEFAULT_JOB_QUEUE)
    }

    @EventListener
    fun onJobMessageReceivedEvent(event: JobMessageReceivedEvent) {
        if (event.message !is JobScheduled) {
            return
        }
        log.debug("Job {} assigned!", event.message.id)
        scheduleForExecution(event.message.id, event.channel, event.deliveryTag)
    }
}

@Serializable
private data class JobData(
    @SerialName("t")
    val type: String,
    @SerialName("d")
    val data: String
)

data class JobMessageReceivedEvent(
    val message: Any,
    val channel: Channel,
    val deliveryTag: Long
) {
    /**
     * Acks the message
     */
    fun ack() {
        channel.basicAck(deliveryTag, true)
    }
}

/**
 * Queue listener listening for jobs
 */
class JobQueueListener(
    val publisher: ApplicationEventPublisher,
    val scheduler: JobManager
) : ChannelAwareMessageListener {
    @OptIn(ExperimentalSerializationApi::class)
    override fun onMessage(message: Message?, channel: Channel?) {
        if (message == null || channel == null)
            return
        log.trace("Received {} on channel {}", message, channel)
        val type = message.messageProperties.headers["type"].toString()
        val clazz = try {
            Class.forName(type)
        } catch (e: ClassNotFoundException) {
            log.warn("No class found for type {}. Dropping message", type)
            channel.basicAck(message.messageProperties.deliveryTag, false)
            return
        }
        val serializer = serializer(clazz)
        val deserialized = Json.decodeFromString(serializer, message.body.decodeToString())
        publisher.publishEvent(
            JobMessageReceivedEvent(
                deserialized,
                channel,
                message.messageProperties.deliveryTag
            )
        )
    }
}