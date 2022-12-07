package com.mrkirby153.giveaways.config

import com.mrkirby153.giveaways.utils.CoroutineTransactionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.support.TransactionTemplate

/**
 * Database configuration
 */
@Configuration
class DatabaseConfiguration(
    private val template: TransactionTemplate
) {

    @Bean
    fun coroutineTransactionHandler() = CoroutineTransactionHandler(template)
}