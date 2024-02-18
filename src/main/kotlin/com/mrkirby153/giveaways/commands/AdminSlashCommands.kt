package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.coroutine.await
import me.mrkirby153.kcutils.Time
import org.springframework.stereotype.Component

@Component
class AdminSlashCommands : DslSlashCommandProvider {
    override fun register(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("ping") {
                description = "Checks the bot's ping"
                run {
                    val start = System.currentTimeMillis()
                    val hook = deferReply(true).await()
                    hook.editOriginal(
                        "Pong! Took ${
                            Time.format(
                                1,
                                System.currentTimeMillis() - start
                            )
                        }"
                    ).await()
                }
            }
        }
    }
}