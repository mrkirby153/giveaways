package com.mrkirby153.giveaways.service

import com.mrkirby153.giveaways.events.GiveawayEndingEvent
import com.mrkirby153.giveaways.events.GiveawayStartedEvent
import com.mrkirby153.giveaways.jobs.GiveawayEndJob
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.utils.log
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import org.quartz.JobBuilder
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import javax.persistence.EntityNotFoundException

interface GiveawayService {

    /**
     * Starts a giveaway
     */
    fun start(
        channel: TextChannel,
        name: String,
        endsAt: Long,
        winners: Int,
        host: User
    ): CompletableFuture<GiveawayEntity>

    /**
     * Ends a giveaway
     */
    fun end(entity: GiveawayEntity)

    /**
     * Looks up a giveaway by [messageId]
     */
    fun lookupByMessageId(messageId: String): GiveawayEntity?

    /**
     * Looks up a giveaway by [id]
     */
    fun lookupById(id: Long): GiveawayEntity?

    /**
     * Looks up a giveaway by its id or the snowflake of the message id
     */
    fun lookupByIdOrMessageId(idOrSnowflake: String): GiveawayEntity?
}

private val snowflakeRegex = Pattern.compile("\\d{17,20}")

@Service
class GiveawayManager(
    private val giveawayRepository: GiveawayRepository,
    private val giveawayMessageService: GiveawayMessageService,
    private val eventPublisher: ApplicationEventPublisher,
    private val jobScheduler: Scheduler
) : GiveawayService {

    override fun start(
        channel: TextChannel,
        name: String,
        endsAt: Long,
        winners: Int,
        host: User
    ): CompletableFuture<GiveawayEntity> {
        if (!channel.canTalk()) {
            return CompletableFuture.failedFuture(IllegalStateException("Cannot talk in channel"))
        }
        if (!channel.guild.selfMember.hasPermission(Permission.MESSAGE_EMBED_LINKS)) {
            return CompletableFuture.failedFuture(IllegalStateException("Missing embed permissions"))
        }
        var entity = GiveawayEntity(
            name,
            channel.guild.id,
            channel.id,
            "",
            winners,
            host = host.id,
            endsAt = Timestamp.from(
                Instant.ofEpochMilli(endsAt)
            )
        )
        entity = giveawayRepository.save(entity)
        val msg = giveawayMessageService.render(entity)
        return channel.sendMessage(msg.create()).submit().thenApply {
            entity.messageId = it.id
            val result = giveawayRepository.save(entity)
            eventPublisher.publishEvent(GiveawayStartedEvent(result))
            result
        }
    }

    override fun end(entity: GiveawayEntity) {
        entity.state = GiveawayState.ENDING
        val newEntity = giveawayRepository.save(entity)
        eventPublisher.publishEvent(GiveawayEndingEvent(newEntity))
    }

    override fun lookupByMessageId(messageId: String): GiveawayEntity? =
        giveawayRepository.getFirstByMessageId(messageId)

    override fun lookupById(id: Long): GiveawayEntity? = try {
        giveawayRepository.getReferenceById(id)
    } catch (e: EntityNotFoundException) {
        null
    }

    override fun lookupByIdOrMessageId(idOrSnowflake: String): GiveawayEntity? {
        val matcher = snowflakeRegex.matcher(idOrSnowflake)
        return if (matcher.find()) {
            // Look up by message id
            lookupByMessageId(idOrSnowflake)
        } else {
            lookupById(idOrSnowflake.toLong())
        }
    }

    private fun scheduleEndJob(giveaway: GiveawayEntity) {
        val key = JobKey.jobKey("${giveaway.id}_end", "giveaways")
        val triggerKey = TriggerKey.triggerKey("${giveaway.id}_end")
        val existing = jobScheduler.getJobDetail(key)
        if (existing != null) {
            log.debug("Rescheduling giveaway ${giveaway.id}")
            val trigger = jobScheduler.getTrigger(triggerKey).triggerBuilder
            val newTrigger = trigger.startAt(giveaway.endsAt).build()
            jobScheduler.rescheduleJob(triggerKey, newTrigger)
        } else {
            log.debug("Scheduling giveaway ${giveaway.id}")
            val trigger =
                TriggerBuilder.newTrigger().withIdentity(triggerKey).startAt(giveaway.endsAt)
                    .build()
            val job = JobBuilder.newJob(GiveawayEndJob::class.java).withIdentity(key).build()
            val data = job.jobDataMap
            data["id"] = giveaway.id.toString()
            jobScheduler.scheduleJob(job, trigger)
        }
    }


    @EventListener
    fun onGiveawayStart(event: GiveawayStartedEvent) {
        scheduleEndJob(event.giveaway)
    }

}