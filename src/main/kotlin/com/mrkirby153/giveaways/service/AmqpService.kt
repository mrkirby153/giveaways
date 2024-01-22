package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.utils.SLF4J
import com.mrkirby153.giveaways.config.BROADCAST_EXCHANGE
import com.mrkirby153.giveaways.events.GiveawayEndedEvent
import com.mrkirby153.giveaways.events.GiveawayStartedEvent
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.amqp.core.ExchangeTypes
import org.springframework.amqp.rabbit.AsyncRabbitTemplate
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service


interface AmqpService {

    suspend fun <Resp : AmqpMessage?> rpc(
        req: AmqpRequest<Resp>,
        destNode: String,
        type: Class<Resp>
    ): Resp?

    fun send(msg: AmqpMessage, destNode: String)

    fun broadcast(msg: AmqpMessage)
}

suspend inline fun <reified Resp : AmqpMessage?> AmqpService.rpc(
    req: AmqpRequest<Resp>,
    destNode: String
) =
    rpc(req, destNode, Resp::class.java)

@RabbitListener(
    bindings = [QueueBinding(
        Queue(), exchange = Exchange(BROADCAST_EXCHANGE, type = ExchangeTypes.FANOUT)
    )]
)
@RabbitListener(
    queues = ["giveaways_#{rabbitMqConfiguration.nodeIdentifier}"]
)
@Service
class AmqpManager(
    private val asyncRabbitTemplate: AsyncRabbitTemplate,
    private val rabbitTemplate: RabbitTemplate,
    private val publisher: ApplicationEventPublisher,
    private val giveawayRepository: GiveawayRepository,
    private val shardManager: ShardManager
) : AmqpService {

    private val log by SLF4J

    override suspend fun <Resp : AmqpMessage?> rpc(
        req: AmqpRequest<Resp>, destNode: String, type: Class<Resp>
    ): Resp? {
        log.trace("RPC call -> {}: {} -> {}", destNode, req, type)
        val resp = asyncRabbitTemplate.convertSendAndReceiveAsType(
            "giveaways_${destNode}",
            req,
            ParameterizedTypeReference.forType<Resp>(type)
        )
        return resp.await()
    }

    override fun send(msg: AmqpMessage, destNode: String) {
        val routingKey = "giveaways_${destNode}"
        log.trace("Sending {} -> {}", msg, routingKey)
        rabbitTemplate.convertAndSend(routingKey, msg)
    }

    override fun broadcast(msg: AmqpMessage) {
        log.trace("Broadcasting {}", msg)
        rabbitTemplate.convertAndSend(BROADCAST_EXCHANGE, "", msg)
    }


    // Handlers
    @RabbitHandler
    fun onReceive(message: AmqpMessage.GiveawayEnded) {
        log.trace("Received giveaway ended for ${message.giveawayId}")
        val giveaway = giveawayRepository.findByIdOrNull(message.giveawayId) ?: return
        // Check if we're the shard responsible for this event
        val guild = shardManager.getGuildById(giveaway.guildId)
        if (guild == null) {
            log.trace("Dropping end message for {} as we don't own it.", giveaway)
            return
        }
        publisher.publishEvent(GiveawayEndedEvent(message.giveawayId))
    }

    @RabbitHandler
    fun onReceive(message: AmqpMessage.GiveawayStarted) {
        log.trace("Received giveaway started ${message.giveawayId}")
        publisher.publishEvent(GiveawayStartedEvent(message.giveawayId))
    }
}

sealed interface AmqpRequest<Resp : AmqpMessage?>

sealed interface AmqpMessage {
    data class GiveawayEnded(val giveawayId: Long) : AmqpMessage

    data class GiveawayStarted(val giveawayId: Long) : AmqpMessage
}
