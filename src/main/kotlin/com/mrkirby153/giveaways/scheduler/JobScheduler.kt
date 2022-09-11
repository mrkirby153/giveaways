package com.mrkirby153.giveaways.scheduler

import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.giveaways.scheduler.jpa.JobEntity
import com.mrkirby153.giveaways.scheduler.jpa.JobRepository
import com.mrkirby153.giveaways.scheduler.message.CancelJob
import com.mrkirby153.giveaways.scheduler.message.GlobalMessageService
import com.mrkirby153.giveaways.scheduler.message.JobScheduled
import com.mrkirby153.giveaways.utils.log
import com.rabbitmq.client.Channel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.sql.Timestamp
import javax.transaction.Transactional


const val DEFAULT_JOB_QUEUE = "default"

const val JOB_QUEUE_FORMAT = "queue_%s"

interface JobScheduler {
    fun <T : Any> schedule(job: Job<T>, runAt: Timestamp, queue: String = DEFAULT_JOB_QUEUE): Long

    fun cancel(jobId: Long): Boolean

    fun listen(queue: String)

    fun unlisten(queue: String)
}

@Service
class JobManager(
    private val globalMessageService: GlobalMessageService,
    private val repository: JobRepository,
    private val admin: AmqpAdmin,
    private val rabbitTemplate: RabbitTemplate,
    private val connectionFactory: ConnectionFactory,
    private val publisher: ApplicationEventPublisher
) : JobScheduler {

    private val listeningQueues = mutableMapOf<String, SimpleMessageListenerContainer>()

    override fun <T : Any> schedule(
        job: Job<T>,
        runAt: Timestamp,
        queue: String
    ): Long {
        log.debug("Scheduling {} to run at {}", job, runAt)
        val serializedData = Json.encodeToString(getSerializer(job.data), job.data)
        val jobClass = job::class.java.canonicalName
        val entity = repository.save(JobEntity(jobClass, serializedData, queue, runAt))
        publish(queue, JobScheduled(entity.id, entity.runAt.time))
        return entity.id
    }

    @Transactional
    override fun cancel(jobId: Long): Boolean {
        log.debug("Canceling {}", jobId)
        repository.findById(jobId).ifPresent {
            globalMessageService.broadcast(CancelJob(it.id))
            repository.delete(it)
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
            setPrefetchCount(9999)
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

    @EventListener
    fun onReady(event: BotReadyEvent) {
        listen(DEFAULT_JOB_QUEUE)
    }
}

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