package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.spring.config.EnableBot
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.annotation.Configuration

@EnableBot
@Configuration
class BotConfiguration(
    dslCommandExecutor: DslCommandExecutor,
    shardManager: ShardManager,
) {

    init {
        shardManager.addEventListener(dslCommandExecutor.getListener())
    }

}