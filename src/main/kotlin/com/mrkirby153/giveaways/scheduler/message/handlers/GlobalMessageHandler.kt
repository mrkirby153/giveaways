package com.mrkirby153.giveaways.scheduler.message.handlers


/**
 * A handler for global messages
 */
interface GlobalMessageHandler<T : Any> {
    /**
     * Handle the provided [message]
     */
    fun handle(message: T)
}