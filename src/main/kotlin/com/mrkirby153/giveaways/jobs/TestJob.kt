package com.mrkirby153.giveaways.jobs

import com.mrkirby153.giveaways.scheduler.Job
import com.mrkirby153.giveaways.utils.log

class TestJob : Job<String>() {
    override fun run() {
        log.info("Hello $data")
    }
}