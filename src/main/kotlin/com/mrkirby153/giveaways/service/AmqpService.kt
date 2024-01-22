package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.utils.SLF4J
import com.mrkirby153.giveaways.config.BROADCAST_EXCHANGE
import com.mrkirby153.giveaways.config.RabbitMQConfiguration
import kotlinx.coroutines.future.await
import org.springframework.amqp.core.ExchangeTypes
import org.springframework.amqp.rabbit.AsyncRabbitTemplate
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service


interface AmqpService {
    suspend fun <Resp : AmqpMessage?> rpc(
        req: AmqpRequest<Resp>,
        destNode: String,
        type: Class<Resp>
    ): Resp?
}

suspend inline fun <reified Resp : AmqpMessage?> AmqpService.rpc(req: AmqpRequest<Resp>, destNode: String) =
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
    private val rabbitMQConfiguration: RabbitMQConfiguration, val template: AsyncRabbitTemplate
) : AmqpService {

    private val log by SLF4J

    override suspend fun <Resp : AmqpMessage?> rpc(
        req: AmqpRequest<Resp>, destNode: String, type: Class<Resp>
    ): Resp? {
        val resp = template.convertSendAndReceiveAsType(
            "giveaways_${destNode}",
            req,
            ParameterizedTypeReference.forType<Resp>(type)
        )
        return resp.await()
    }

    @RabbitHandler
    fun handleRequest(message: TestRequest): TestResponse {
        log.info("Received TestRequest")
        return TestResponse(message.num1!! + message.num2!!)
    }
}

sealed interface AmqpRequest<Resp : AmqpMessage?>

sealed interface AmqpMessage

data class TestRequest(val num1: Int?, val num2: Int?) : AmqpRequest<TestResponse>

data class TestResponse(val result: Int?) : AmqpMessage