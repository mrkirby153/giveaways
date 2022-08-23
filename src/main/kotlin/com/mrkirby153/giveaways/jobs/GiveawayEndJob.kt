package com.mrkirby153.giveaways.jobs

import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.service.GiveawayService
import com.mrkirby153.giveaways.utils.log
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.springframework.stereotype.Component

@Component
class GiveawayEndJob(
    private val giveawayRepository: GiveawayRepository,
    private val giveawayService: GiveawayService
) : Job {

    override fun execute(context: JobExecutionContext) {
        val data = context.jobDetail.jobDataMap
        val giveawayId = data.getLongFromString("id")
        giveawayRepository.findById(giveawayId).ifPresent {
            log.info("Ending giveaway $it")
            giveawayService.end(it)
        }
    }
}