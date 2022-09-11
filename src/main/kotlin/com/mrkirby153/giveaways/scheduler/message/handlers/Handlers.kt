package com.mrkirby153.giveaways.scheduler.message.handlers

import com.mrkirby153.giveaways.scheduler.message.CancelTask
import com.mrkirby153.giveaways.utils.log
import org.springframework.stereotype.Component

@Component
class HandleCancel : GlobalMessageHandler<CancelTask> {
    override fun handle(message: CancelTask) {
        log.info("canceling task ${message.id}")
    }

}