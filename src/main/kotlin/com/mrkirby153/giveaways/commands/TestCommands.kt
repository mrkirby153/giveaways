package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.long
import com.mrkirby153.giveaways.jobs.TestJob
import com.mrkirby153.giveaways.scheduler.JobScheduler
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

class Testing : Arguments() {
    val id by long {
        name = "id"
        description = "id"
    }.required()
}

@Component
class TestCommands(
    private val jobScheduler: JobScheduler
) : DslSlashCommandProvider {
    override fun register(executor: DslCommandExecutor) {
        executor.slashCommand<Testing> {
            name = "testing"
            description = "testing"
            action {
                val job = TestJob()
                job.data = "jeff"
                jobScheduler.schedule(job, Timestamp.from(Instant.now().plusSeconds(30)))
                reply("Done!").setEphemeral(true).queue()
            }
        }
    }
}