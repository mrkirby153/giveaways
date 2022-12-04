package com.mrkirby153.giveaways.config

import com.mrkirby153.botcore.coroutine.CoroutineEventListener
import com.mrkirby153.botcore.utils.SLF4J
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.annotation.Configuration

@Configuration
class EventListenerConfiguration(
    coroutineEventListeners: List<CoroutineEventListener>,
    shardManager: ShardManager
) {
    private val log by SLF4J

    init {
        log.info("Registering {} coroutine event listeners", coroutineEventListeners.size)
        shardManager.addEventListener(*coroutineEventListeners.toTypedArray())
    }
}