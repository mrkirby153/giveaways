package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.long
import com.mrkirby153.giveaways.scheduler.message.CancelTask
import com.mrkirby153.giveaways.scheduler.message.GlobalMessageService
import org.springframework.stereotype.Component

class Testing : Arguments() {
    val id by long {
        name = "id"
        description = "id"
    }.required()
}

@Component
class TestCommands(
    private val globalMessageService: GlobalMessageService
) : DslSlashCommandProvider {
    override fun register(executor: DslCommandExecutor) {
        executor.slashCommand<Testing> {
            name = "testing"
            description = "testing"
            action {
                globalMessageService.broadcast(CancelTask(args.id))
                this.reply("Done!").setEphemeral(true).queue()
            }
        }
    }
}