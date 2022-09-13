package com.mrkirby153.giveaways.scheduler.message.handlers

import com.mrkirby153.giveaways.scheduler.JobScheduler
import com.mrkirby153.giveaways.scheduler.message.CancelJob
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