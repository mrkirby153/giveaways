package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.builder.ActionRowBuilder
import com.mrkirby153.botcore.builder.MessageBuilder
import com.mrkirby153.botcore.builder.message
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.utils.pluralize
import jakarta.transaction.Transactional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Service
import java.awt.Color

interface GiveawayMessageService {
    fun render(giveawayEntity: GiveawayEntity): MessageBuilder

    suspend fun getMessage(giveawayEntity: GiveawayEntity): Message?

    suspend fun updateMessage(giveawayEntity: GiveawayEntity)
}

@Service
@Transactional
class GiveawayMessageManager(
    private val shardManager: ShardManager
) : GiveawayMessageService {

    override fun render(giveawayEntity: GiveawayEntity): MessageBuilder {
        return when (giveawayEntity.state) {
            GiveawayState.RUNNING -> renderRunning(giveawayEntity)
            GiveawayState.ENDED -> renderEnded(giveawayEntity)
            GiveawayState.ENDING -> renderEnding(giveawayEntity)
        }
    }

    override suspend fun getMessage(giveawayEntity: GiveawayEntity): Message? {
        val guild = shardManager.getGuildById(giveawayEntity.guildId) ?: return null
        val channel = guild.getTextChannelById(giveawayEntity.channelId) ?: return null
        return try {
            channel.retrieveMessageById(giveawayEntity.messageId).await()
        } catch (e: ErrorResponseException) {
            null
        } catch (e: InsufficientPermissionException) {
            null
        }
    }

    override suspend fun updateMessage(giveawayEntity: GiveawayEntity) {
        getMessage(giveawayEntity)?.editMessage(withContext(Dispatchers.IO) {
            render(giveawayEntity).edit()
        })?.await()
    }

    private fun renderRunning(entity: GiveawayEntity): MessageBuilder = message {
        embed {
            color {
                color = Color.GREEN
            }
            description = buildString {
                appendLine("**${entity.name}**")
                appendLine()
                appendLine("Click the button below to enter!")
                val endsAt = entity.endsAt.time / 1000
                appendLine()
                appendLine("Ends <t:$endsAt:R>")
                if (entity.host != null) {
                    appendLine("Hosted by <@!${entity.host}>")
                }
            }
            actionRow {
                renderGiveawayButton(entity)
            }
            footer {
                text = "${entity.id} | ${pluralize(entity.winners, "winner", "winners")}"
            }
        }
    }

    private fun renderEnded(entity: GiveawayEntity): MessageBuilder = message {
        embed {
            color {
                color = Color.RED
            }
            description = buildString {
                appendLine("**${entity.name}**")
                appendLine()
                appendLine("Giveaway has ended!")
                appendLine()
                appendLine("Ended: <t:${entity.endsAt.time / 1000}:f>")
                appendLine()
                if (entity.getWinners().isEmpty()) {
                    appendLine()
                    appendLine("Nobody won the giveaway")
                } else {
                    val winnersAsMention = entity.getWinners().map { "<@!${it}>" }.toMutableList()
                    val iterator = winnersAsMention.iterator()
                    append("**${pluralize(winnersAsMention.size, "Winner", "Winners", false)}:** ")
                    while (iterator.hasNext() && length < 1900) {
                        append(iterator.next())
                        iterator.remove()
                    }
                    if (winnersAsMention.isNotEmpty()) {
                        append("and ${winnersAsMention.size} more!")
                    }
                }
            }
            actionRow {
                renderGiveawayButton(entity)
            }
            footer {
                text = "${entity.id} | ${pluralize(entity.winners, "winner", "winners")}"
            }
        }
    }

    private fun renderEnding(entity: GiveawayEntity): MessageBuilder = message {
        embed {
            color {
                color = Color.RED
            }
            description = buildString {
                appendLine("Giveaway has ended!")
                appendLine()
                appendLine("Determining winners...")
            }
            actionRow {
                renderGiveawayButton(entity)
            }
            footer {
                text = "${entity.id} | ${pluralize(entity.winners, "winner", "winners")}"
            }
        }
    }

    private fun ActionRowBuilder.renderGiveawayButton(giveawayEntity: GiveawayEntity) {
        button(giveawayEntity.interactionUuid) {
            style = ButtonStyle.SUCCESS
            enabled = giveawayEntity.state == GiveawayState.RUNNING
            text = "Enter Giveaway"
        }
    }
}