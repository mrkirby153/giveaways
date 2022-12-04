package com.mrkirby153.giveaways.service

import com.mrkirby153.giveaways.events.GiveawayEndingEvent
import com.mrkirby153.giveaways.events.GiveawayStartedEvent
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
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
    private val eventPublisher: ApplicationEventPublisher
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
    }


    @EventListener
    fun onGiveawayStart(event: GiveawayStartedEvent) {
        scheduleEndJob(event.giveaway)
    }

}