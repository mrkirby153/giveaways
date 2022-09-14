package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.long
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.giveaways.jobs.TestJob
import com.mrkirby153.giveaways.scheduler.JobScheduler
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

class ScheduleArgs : Arguments() {
    val name by string {
        name = "name"
        description = "The person to greet"
    }.required()

    val delay by duration {
        name = "duration"
        description = "The duration to greet"
    }.required()
}

class CancelArgs : Arguments() {
    val id by long {
        name = "id"
        description = "The id of the task to cancel"
    }.required()
}

class RescheduleArgs : Arguments() {
    val id by long {
        name = "id"
        description = "The id of the task to reschedule"
    }.required()

    val newTime by duration {
        name = "new_time"
        description = "The new duration to reschedule"
    }.required()
}

@Component
class TestCommands(
    private val jobScheduler: JobScheduler
) : DslSlashCommandProvider {
    override fun register(executor: DslCommandExecutor) {
        executor.slashCommand<ScheduleArgs> {
            name = "schedule"
            description = "scheduled a task"
            action {
                val job = TestJob()
                job.data = args.name
                val id =
                    jobScheduler.schedule(job, Timestamp.from(Instant.now().plusMillis(args.delay)))
                reply("Scheduled task with id $id").setEphemeral(true).queue()
            }
        }

        executor.slashCommand<CancelArgs> {
            name = "cancel"
            description = "cancels a task"
            action {
                jobScheduler.cancel(args.id)
                reply("Canceled task").setEphemeral(true).queue()
            }
        }

        executor.slashCommand<RescheduleArgs> {
            name = "reschedule"
            description = "reschedule a task"
            action {
                jobScheduler.reschedule(
                    args.id,
                    Timestamp.from(Instant.now().plusMillis(args.newTime))
                )
                reply("Rescheduled task with id ${args.id}").setEphemeral(true).queue()
            }
        }
    }
}