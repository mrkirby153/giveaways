package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.giveaways.events.GiveawayEndingEvent
import com.mrkirby153.giveaways.events.GiveawayStartedEvent
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.regex.Pattern
import javax.persistence.EntityNotFoundException

interface GiveawayService {

    /**
     * Starts a giveaway
     */
    suspend fun start(
        channel: TextChannel,
        name: String,
        endsAt: Long,
        winners: Int,
        host: User
    ): GiveawayEntity

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

    override suspend fun start(
        channel: TextChannel,
        name: String,
        endsAt: Long,
        winners: Int,
        host: User
    ): GiveawayEntity {
        check(channel.canTalk()) { "Can't talk in channel ${channel.name}" }
        check(
            channel.guild.selfMember.hasPermission(
                channel,
                Permission.MESSAGE_EMBED_LINKS
            )
        ) { "Missing embed permissions" }
        var entity = withContext(Dispatchers.IO) {
            giveawayRepository.save(
                GiveawayEntity(
                    name,
                    channel.guild.id,
                    channel.id,
                    "",
                    winners,
                    host.id,
                    null, Timestamp.from(Instant.ofEpochMilli(endsAt))
                )
            )
        }
        val msg = giveawayMessageService.render(entity)
        val discordMessage = channel.sendMessage(msg.create()).await()
        entity.messageId = discordMessage.id
        entity = withContext(Dispatchers.IO) {
            giveawayRepository.save(entity)
        }
        eventPublisher.publishEvent(GiveawayStartedEvent(entity))
        return entity
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