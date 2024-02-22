package com.mrkirby153.giveaways.config

import com.mrkirby153.botcore.coroutine.CoroutineEventListener
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.annotation.Configuration

private val log = KotlinLogging.logger { }

@Configuration
class EventListenerConfiguration(
    coroutineEventListeners: List<CoroutineEventListener>,
    shardManager: ShardManager
) {

    init {
        log.info { "Registering ${coroutineEventListeners.size} coroutine event listeners" }
        shardManager.addEventListener(*coroutineEventListeners.toTypedArray())
    }
}