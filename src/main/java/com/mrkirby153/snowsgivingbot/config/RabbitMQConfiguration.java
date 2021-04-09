package com.mrkirby153.snowsgivingbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfiguration {

    public static final String GIVEAWAY_STATE_EXCHANGE = "giveaway_state";
    public static final String GIVEAWAY_WORK_QUEUE = "giveaways";

    @Bean
    public Exchange globalStateQueue() {
        return new FanoutExchange(GIVEAWAY_STATE_EXCHANGE);
    }

    @Bean
    public Queue giveawayWorkerQueue() {
        return new Queue(GIVEAWAY_WORK_QUEUE);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
