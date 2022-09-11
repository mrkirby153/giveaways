package com.mrkirby153.giveaways.scheduler.message

import com.mrkirby153.giveaways.config.BROADCAST_EXCHANGE
import com.mrkirby153.giveaways.scheduler.message.handlers.GlobalMessageHandler
import com.mrkirby153.giveaways.scheduler.message.handlers.HandleCancel
import com.mrkirby153.giveaways.utils.log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

/**
 * Handler class for global messages between the cluster
 */
@Service
class GlobalMessageService(
    val template: RabbitTemplate,
    private val context: ApplicationContext
) {

    private var nextId = 1L
    val registeredMessages = mutableMapOf<Long, Class<*>>()
    private val handlers = mutableMapOf<Long, GlobalMessageHandler<in Any>>()

    init {
        register(CancelJob::class.java, HandleCancel::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> register(
        message: Class<T>,
        handler: Class<out GlobalMessageHandler<in T>>
    ) {
        val id = nextId++
        this.registeredMessages[id] = message
        this.handlers[id] = context.getBean(handler) as GlobalMessageHandler<in Any>
    }

    /**
     * Serializes a message to be broadcast to the cluster
     */
    final inline fun <reified T : Any> serializeToJson(message: T): String {
        val data = Json.encodeToString(message)
        val id = registeredMessages.entries.firstOrNull { it.value == message.javaClass }?.key
            ?: error("Provided message $message was not registered")
        return Json.encodeToString(GlobalMessageJson(id, data))
    }

    /**
     * Broadcasts the provided [message] to all workers
     */
    final inline fun <reified T : Any> broadcast(message: T) {
        template.convertAndSend(BROADCAST_EXCHANGE,"", serializeToJson(message))
    }

    /**
     * Deserializes a message
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun deserialize(json: String): Pair<Long, Any> {
        val message = Json.decodeFromString<GlobalMessageJson>(json)
        val messageClass = registeredMessages[message.id]
            ?: error("Message with the id ${message.id} was not found")
        val serializer = serializer(messageClass)
        return message.id to Json.decodeFromString(serializer, message.data)
    }

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue(exclusive = "true"),
                exchange = Exchange(name = BROADCAST_EXCHANGE, type = "fanout")
            )
        ]
    )
    fun handleBroadcast(message: String) {
        try {
            val (id, msg) = deserialize(message)
            val handler = handlers[id] ?: return
            try {
                handler.handle(msg)
            } catch (e: Exception) {
                log.error("An exception occurred processing task $id: $msg", e)
            }
        } catch (e: IllegalArgumentException) {
            log.warn("Unhandled message received", e)
        }
    }
}

@Serializable
data class GlobalMessageJson(val id: Long, val data: String)

@Serializable
data class CancelJob(val id: Long)