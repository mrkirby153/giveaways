package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.giveaways.config.LeaderChangeEvent
import com.mrkirby153.giveaways.config.LeadershipAcquiredEvent
import com.mrkirby153.giveaways.config.LeadershipLostEvent
import com.mrkirby153.giveaways.events.GiveawayEndingEvent
import com.mrkirby153.giveaways.events.GiveawayStartedEvent
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.utils.log
import jakarta.persistence.EntityNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.regex.Pattern

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

    /**
     * Gets a list of all giveaways that are running on all shards in the current process
     */
    fun getAllGiveawaysForCurrentProcess(vararg state: GiveawayState): List<GiveawayEntity>
}

private val snowflakeRegex = Pattern.compile("\\d{17,20}")

@Service
class GiveawayManager(
    private val shardManager: ShardManager,
    private val giveawayRepository: GiveawayRepository,
    private val giveawayMessageService: GiveawayMessageService,
    private val eventPublisher: ApplicationEventPublisher,
    private val taskScheduler: TaskScheduler
) : GiveawayService {

    private final val endLock = Any()

    private var endTask: ScheduledFuture<*>? = null
    private var nextRunAt: Long? = null

    /**
     * If this instance is the end task leader
     */
    private var isLeader = false
    private var currentLeader: String? = null

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
        log.debug("Ending giveaway {}", entity)
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

    override fun getAllGiveawaysForCurrentProcess(vararg state: GiveawayState): List<GiveawayEntity> {
        val guilds = shardManager.guildCache.map { it.id }.toList()
        return giveawayRepository.getAllByGuildIdInAndStateIn(
            guilds,
            (if (state.isEmpty()) GiveawayState.values() else state).toList()
        )
    }

    private fun scheduleNextEndsAt(
        runTimestamp: Long? = null
    ) {
        if (!isLeader) {
            log.trace("Ignoring request to schedule next end at, as we are not the leader. Current Leader: $currentLeader")
            return // We are not responsible for ending giveaways
        }
        val runAt =
            runTimestamp ?: giveawayRepository.getNextEnds().firstOrNull()?.endsAt?.time
        synchronized(endLock) {
            if (this.nextRunAt != null && runAt != null) {
                if (runAt > this.nextRunAt!!) {
                    log.debug(
                        "Not re-scheduling as runAt ({}) is greater than the already scheduled task ({})",
                        runAt,
                        this.nextRunAt
                    )
                    return
                }
            }
            if (runAt == null) {
                log.debug("Not scheduling new task as runAt is null")
                return
            }
            endTask?.cancel(false)
            val runInstant = Instant.ofEpochMilli(runAt)
            log.debug(
                "Scheduling new end task at {} (in {})",
                runInstant,
                Time.format(1, runInstant.toEpochMilli() - System.currentTimeMillis())
            )
            endTask = taskScheduler.schedule({
                endAllGiveaways()
            }, runInstant)
            this.nextRunAt = runAt
        }
    }

    private fun endAllGiveaways() {
        if (!isLeader) {
            log.warn("endAllGiveaways called while not leader. Current leader: $currentLeader")
            return
        }
        log.debug("Ending all giveaways")
        synchronized(endLock) {
            try {
                val ended =
                    giveawayRepository.getAllByGuildIdInAndEndsAtIsBeforeAndStateIs(
                        shardManager.guildCache.map { it.id },
                        Timestamp(Instant.now().plusSeconds(1).toEpochMilli()),
                        GiveawayState.RUNNING
                    )
                if (ended.isNotEmpty()) {
                    log.debug("Ending {} giveaways", ended.size)
                }
                ended.forEach {
                    end(it)
                }
            } finally {
                this.nextRunAt = null
                scheduleNextEndsAt()
            }
        }
    }


    @EventListener
    fun onGiveawayStart(event: GiveawayStartedEvent) {
        scheduleNextEndsAt(event.giveaway.endsAt.time)
        // TODO: Broadcast this to the leader
    }

    @EventListener
    fun onBecomeLeader(event: LeadershipAcquiredEvent) {
        log.trace("Acquired leadership")
        this.isLeader = true
        scheduleNextEndsAt()
    }

    @EventListener
    fun onLostLeader(event: LeadershipLostEvent) {
        log.trace("Lost leadership")
        this.isLeader = false
        this.nextRunAt = null
        if (this.endTask?.isDone == false)
            this.endTask?.cancel(true)
    }

    @EventListener
    fun onLeadershipChange(event: LeaderChangeEvent) {
        log.trace("New leader: ${event.newLeader}")
        this.currentLeader = event.newLeader
    }
}