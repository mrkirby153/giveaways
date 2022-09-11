package com.mrkirby153.giveaways.config

import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


const val BROADCAST_EXCHANGE = "job_broadcast"

@Configuration
class RabbitMQConfiguration {

    @Bean
    fun broadcastExchange() = FanoutExchange(BROADCAST_EXCHANGE)

    @Bean
    fun messageConverter() = Jackson2JsonMessageConverter()
}