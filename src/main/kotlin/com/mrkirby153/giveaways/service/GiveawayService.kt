package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.builder.message
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.giveaways.config.LeaderChangeEvent
import com.mrkirby153.giveaways.config.LeadershipAcquiredEvent
import com.mrkirby153.giveaways.config.LeadershipLostEvent
import com.mrkirby153.giveaways.events.GiveawayEndedEvent
import com.mrkirby153.giveaways.events.GiveawayStartedEvent
import com.mrkirby153.giveaways.jpa.EntrantRepository
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.utils.log
import jakarta.persistence.EntityNotFoundException
import jakarta.transaction.Transactional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.mrkirby153.kcutils.Time
import me.mrkirby153.kcutils.coroutines.runAsync
import me.mrkirby153.kcutils.spring.coroutine.jpa.ThreadSafeJpaReference
import me.mrkirby153.kcutils.spring.coroutine.transaction
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.Random
import java.util.concurrent.CompletableFuture
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

    fun getWinners(
        giveaway: GiveawayEntity,
        count: Int = 1,
        exclude: List<String>? = emptyList(),
        existingWinners: List<String>? = emptyList()
    ): Set<String>

    fun rerollGiveaway(
        giveaway: ThreadSafeJpaReference<GiveawayEntity, Long>,
        toReroll: List<String>
    ): List<String>

    fun rerollGiveaway(giveaway: GiveawayEntity, toReroll: List<String>): List<String>

    fun announceWinners(giveaway: GiveawayEntity, winners: List<String>): CompletableFuture<Void>
}

private val snowflakeRegex = Pattern.compile("\\d{17,20}")

@Service
class GiveawayManager(
    private val shardManager: ShardManager,
    private val giveawayRepository: GiveawayRepository,
    private val giveawayEntrantRepository: EntrantRepository,
    private val giveawayMessageService: GiveawayMessageService,
    private val eventPublisher: ApplicationEventPublisher,
    private val taskScheduler: TaskScheduler,
    private val amqpService: AmqpService
) : GiveawayService {

    private final val random: Random = Random()

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
        entity.id?.let {
            log.trace("Sending GiveawayStarted to current leader: ${this.currentLeader}")
            val leader = currentLeader
            if (leader == null) {
                log.warn("No leader established. Dropping start event for $entity")
                return@let
            }
            amqpService.send(AmqpMessage.GiveawayStarted(it), leader)
        }
        return entity
    }

    override fun end(entity: GiveawayEntity) {
        log.debug("Ending giveaway {}", entity)
        entity.state = GiveawayState.ENDING
        val newEntity = giveawayRepository.save(entity)
        amqpService.broadcast(AmqpMessage.GiveawayEnded(newEntity.id!!))
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
            (if (state.isEmpty()) GiveawayState.entries.toTypedArray() else state).toList()
        )
    }

    override fun getWinners(
        giveaway: GiveawayEntity,
        count: Int,
        exclude: List<String>?,
        existingWinners: List<String>?
    ): Set<String> {
        log.trace("Determining {} winners for {}, excluding {}", count, giveaway, exclude)
        val entrants = giveawayEntrantRepository.getAllByGiveaway(giveaway)
        val winners = existingWinners?.toMutableSet() ?: mutableSetOf()
        while (winners.size < count && winners.size < entrants.size) {
            val candidate = entrants[random.nextInt(entrants.size)].userId
            log.trace("Candidate: {}", candidate)
            // Here's where other checks would go
            if (candidate in winners) {
                log.trace("Excluding {} as they are already a winner", candidate)
                continue
            }
            if (exclude != null) {
                if (candidate in exclude) {
                    log.trace("Excluding {} as they are excluded", candidate)
                    continue
                }
            }
            log.trace("{} is a valid winner", candidate)
            winners.add(candidate)
        }
        if (winners.size < count) {
            log.trace("Returning partial winners, as there were not enough entrants")
        }
        return winners.toSet()
    }

    @Transactional
    override fun rerollGiveaway(
        giveaway: ThreadSafeJpaReference<GiveawayEntity, Long>,
        toReroll: List<String>
    ): List<String> {
        val g = giveaway.get()!!
        val oldWinners = g.getWinners()
        val newWinners = getWinners(
            g,
            g.winners,
            toReroll,
            g.getWinners().filter { it !in toReroll })
        g.setWinners(newWinners.toTypedArray())
        log.trace("new winners: {}", newWinners)
        giveawayRepository.save(g)
        val toAnnounce = newWinners - oldWinners.toSet()
        announceWinners(g, toAnnounce.toList())
        runBlocking {
            giveawayMessageService.updateMessage(g)
        }
        return newWinners.toList()
    }

    @Transactional
    override fun rerollGiveaway(giveaway: GiveawayEntity, toReroll: List<String>): List<String> {
        return rerollGiveaway(ThreadSafeJpaReference(giveawayRepository, giveaway.id!!), toReroll)
    }

    override fun announceWinners(
        giveaway: GiveawayEntity,
        winners: List<String>
    ): CompletableFuture<Void> {
        log.trace("Announcing winners {} for giveaway {}", winners, giveaway)
        val channel = shardManager.getTextChannelById(giveaway.channelId)
            ?: return CompletableFuture.failedFuture(IllegalStateException("Channel not found"))
        check(channel.canTalk()) { "Can't send messages in channel" }
        val userFutures = mutableMapOf<String, CompletableFuture<User?>>()

        giveaway.getWinners().forEach { userId ->
            userFutures[userId] =
                shardManager.retrieveUserById(userId).submit().exceptionally {
                    if (it is ErrorResponseException) {
                        if (it.errorResponse == ErrorResponse.UNKNOWN_USER) {
                            log.trace("User $userId not found")
                        }
                    } else {
                        log.error("Could not retrieve user $userId", it)
                    }
                    null
                }
        }

        return CompletableFuture.allOf(*userFutures.values.toTypedArray()).thenCompose {
            val sendMessageFutures = mutableListOf<CompletableFuture<*>>()

            val sentUserIds: MutableList<String> = mutableListOf()
            val toSend = buildString {
                append("Congratulations ")
                winners.forEach { winnerId ->
                    val toAppend = "<@${winnerId}> "
                    if (length + toAppend.length > 1990) {
                        sendMessageFutures.add(channel.sendMessage(message {
                            content = this@buildString.toString()
                            allowMention(Message.MentionType.USER)
                            sentUserIds.forEach { id ->
                                userFutures[id]?.apply {
                                    get()?.apply { mention(this) }
                                }
                            }

                        }.create()).submit())
                        this.clear()
                        sentUserIds.clear()
                        append(toAppend)
                        sentUserIds.add(winnerId)
                    } else {
                        append(toAppend)
                        sentUserIds.add(winnerId)
                    }
                    append("! You won **${giveaway.name}**")
                }
            }
            if (toSend.isNotEmpty()) {
                sendMessageFutures.add(channel.sendMessage(message {
                    content = toSend
                    allowMention(Message.MentionType.USER)
                    sentUserIds.forEach { id ->
                        userFutures[id]?.apply {
                            get()?.apply { mention(this) }
                        }
                    }
                }.create()).submit())
            }

            if (sendMessageFutures.isEmpty()) {
                CompletableFuture.completedFuture(null)
            } else {
                CompletableFuture.allOf(*sendMessageFutures.toTypedArray())
            }
        }
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
        val giveaway =
            event.giveaway ?: giveawayRepository.findByIdOrNull(event.giveawayId) ?: return
        scheduleNextEndsAt(giveaway.endsAt.time)
    }

    @EventListener
    @Transactional
    fun onGiveawayEnd(event: GiveawayEndedEvent) {
        runAsync {
            withContext(Dispatchers.IO) {
                transaction {
                    val endingGiveaway =
                        giveawayRepository.findByIdOrNull(event.giveawayId) ?: return@transaction
                    log.debug("Ending giveaway {}", endingGiveaway)
                    endingGiveaway.state = GiveawayState.ENDING
                    val job = launch {
                        // Only update the message if it takes longer than 5 seconds
                        delay(5000)
                        giveawayMessageService.updateMessage(endingGiveaway)
                    }

                    val winners = getWinners(endingGiveaway, endingGiveaway.winners)
                    log.debug("Winners for {}: {}", endingGiveaway, winners)
                    endingGiveaway.setWinners(winners.toTypedArray())
                    endingGiveaway.state = GiveawayState.ENDED
                    if (job.isActive)
                        job.cancel()
                    giveawayMessageService.updateMessage(endingGiveaway)
                    announceWinners(endingGiveaway, winners.toList())
                    giveawayRepository.save(endingGiveaway)
                }
            }
        }.join()
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