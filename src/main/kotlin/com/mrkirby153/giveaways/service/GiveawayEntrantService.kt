package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.coroutine.CoroutineEventListener
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.botcore.spring.event.BotReadyEvent
import com.mrkirby153.botcore.utils.SLF4J
import com.mrkirby153.giveaways.events.GiveawayEndingEvent
import com.mrkirby153.giveaways.events.GiveawayStartedEvent
import com.mrkirby153.giveaways.jpa.EntrantRepository
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayEntrantEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface GiveawayEntrantService {

    /**
     * Enters the given [user] into the provided [giveaway]
     */
    suspend fun enter(giveaway: GiveawayEntity, user: User)

    /**
     * Checks if the given [user] is entered into [giveaway]
     */
    suspend fun isEntered(giveaway: GiveawayEntity, user: User): Boolean
}

@Service
class GiveawayEntrantManager(
    private val entrantRepository: EntrantRepository,
    private val giveawayRepository: GiveawayRepository
) : GiveawayEntrantService, CoroutineEventListener {
    private val log by SLF4J

    private val loadedButtons = CopyOnWriteArraySet<String>()

    override suspend fun enter(giveaway: GiveawayEntity, user: User) {
        if (withContext(Dispatchers.IO) {
                entrantRepository.existsByGiveawayAndUserId(giveaway, user.id)
            }) {
            log.debug("{} has already entered {}", user, giveaway)
            throw AlreadyEnteredException(user, giveaway)
        } else {
            if (giveaway.state != GiveawayState.RUNNING) {
                log.debug("Not entering {} into {}. Has already ended", user, giveaway)
                return
            }
            log.debug("Entering {} into {}", user, giveaway)
            withContext(Dispatchers.IO) {
                entrantRepository.save(GiveawayEntrantEntity(giveaway, user.id))
            }
        }
    }

    override suspend fun isEntered(giveaway: GiveawayEntity, user: User) =
        withContext(Dispatchers.IO) {
            entrantRepository.existsByGiveawayAndUserId(giveaway, user.id)
        }

    @EventListener
    fun onReady(event: BotReadyEvent) {
        log.debug("Caching giveaway interactions")
        val uuids = giveawayRepository.getAllInteractionUuidsForRunningGiveaways()
        loadedButtons.addAll(uuids)
        log.debug("Cached {} interactions", uuids.size)
    }

    @EventListener
    fun onGiveawayStart(event: GiveawayStartedEvent) {
        log.debug("Caching interaction {}", event.giveaway.interactionUuid)
        loadedButtons.add(event.giveaway.interactionUuid)
    }

    @EventListener
    fun onGiveawayEnd(event: GiveawayEndingEvent) {
        log.debug("Uncaching interaction {}", event.giveaway.interactionUuid)
        loadedButtons.remove(event.giveaway.interactionUuid)
    }

    override suspend fun onEvent(event: GenericEvent) {
        when (event) {
            is ButtonInteractionEvent -> {
                coroutineScope {
                    if (event.componentId !in loadedButtons) {
                        return@coroutineScope
                    }
                    val entity = withContext(Dispatchers.IO) {
                        giveawayRepository.getByInteractionUuid(event.componentId)
                    }
                    if (entity == null) {
                        log.warn("Giveaway not found with interaction uuid: {}", event.componentId)
                        return@coroutineScope
                    }
                    val hook = event.deferReply(true).await()
                    val job = launch {
                        try {
                            enter(entity, event.user)
                            hook.editOriginal("You have been entered into ${entity.name}").await()
                        } catch (e: AlreadyEnteredException) {
                            hook.editOriginal("You've already entered into ${entity.name}").await()
                        }
                    }
                    launch {
                        delay(5.toDuration(DurationUnit.SECONDS))
                        if (job.isActive) {
                            // It's taking a long time to save, tell the user
                            hook.editOriginal("It's taking a long time to enter. Rest assured, your entry will be counted")
                                .await()
                        }
                    }
                }
            }
        }
    }
}

class AlreadyEnteredException(user: User, giveaway: GiveawayEntity) :
    Exception("$user already entered into $giveaway")