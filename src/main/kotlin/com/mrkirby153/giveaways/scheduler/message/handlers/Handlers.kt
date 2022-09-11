package com.mrkirby153.giveaways.scheduler.message.handlers

import com.mrkirby153.giveaways.scheduler.message.CancelJob
import com.mrkirby153.giveaways.utils.log
import org.springframework.stereotype.Component

@Component
class HandleCancel : GlobalMessageHandler<CancelJob> {
    override fun handle(message: CancelJob) {
        log.info("canceling task ${message.id}")
    }

}