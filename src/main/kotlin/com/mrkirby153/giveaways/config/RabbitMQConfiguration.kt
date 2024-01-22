package com.mrkirby153.giveaways.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mrkirby153.botcore.utils.SLF4J
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.AsyncRabbitTemplate
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


const val BROADCAST_EXCHANGE = "giveaways_broadcast"

@Configuration("rabbitMqConfiguration")
class RabbitMQConfiguration(
    @Value("\${giveaways.nodeidentifier}")
    val nodeIdentifier: String
) {

    private val log by SLF4J

    init {
        log.info("Initializing as $nodeIdentifier")
    }

    @Bean
    fun messageConverter() = Jackson2JsonMessageConverter(jacksonObjectMapper())

    @Bean
    fun asyncRabbitTemplate(rabbitTemplate: RabbitTemplate) = AsyncRabbitTemplate(rabbitTemplate)

    @Bean
    fun queue() = Queue("giveaways_${nodeIdentifier}", true, true, false)
}