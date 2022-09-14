package com.mrkirby153.giveaways.scheduler.message.handlers

import com.mrkirby153.giveaways.scheduler.JobScheduler
import com.mrkirby153.giveaways.scheduler.message.CancelJob
import com.mrkirby153.giveaways.scheduler.message.RescheduleJob
import com.mrkirby153.giveaways.utils.log
import org.springframework.stereotype.Component

@Component
class HandleCancel(
    val scheduler: JobScheduler
) : GlobalMessageHandler<CancelJob> {
    override fun handle(message: CancelJob) {
        log.debug("Handling global broadcast to cancel job {}", message.id)
        scheduler.cancel(message.id, false) // Don't broadcast to prevent an infinite loop
    }
}

@Component
class HandleReschedule(
    val scheduler: JobScheduler
) : GlobalMessageHandler<RescheduleJob> {
    override fun handle(message: RescheduleJob) {
        log.debug("Handling global reschedule of job {}", message)
        scheduler.reschedule(
            message.id,
            message.timestamp,
            false
        ) // Don't broadcast to prevent an infinite loop
    }

}