package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.spring.config.EnableBot
import com.mrkirby153.interactionmenus.MenuManager
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@EnableBot
@Configuration
class BotConfiguration(
    dslCommandExecutor: DslCommandExecutor,
    private val shardManager: ShardManager,
) {

    init {
        shardManager.addEventListener(dslCommandExecutor.getListener())
    }

    @Bean
    fun menuManager(): MenuManager {
        val manager = MenuManager()
        shardManager.addEventListener(manager)
        return manager
    }

}