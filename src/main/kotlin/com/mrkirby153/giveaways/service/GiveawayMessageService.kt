package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.builder.ActionRowBuilder
import com.mrkirby153.botcore.builder.MessageBuilder
import com.mrkirby153.botcore.builder.message
import com.mrkirby153.giveaways.events.GiveawayEndingEvent
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.utils.pluralize
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.awt.Color

interface GiveawayMessageService {
    fun render(giveawayEntity: GiveawayEntity): MessageBuilder
}

@Service
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

    @EventListener
    fun onEnding(event: GiveawayEndingEvent) {
        val giveaway = event.giveaway
        val guild = shardManager.getGuildById(giveaway.guildId) ?: return
        val channel = guild.getTextChannelById(giveaway.channelId) ?: return
        channel.retrieveMessageById(giveaway.messageId).queue { msg ->
            msg.editMessage(render(event.giveaway).edit()).queue()
        }
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
                appendLine("Giveaway has ended!")
                if (entity.getWinners().isEmpty()) {
                    appendLine()
                    appendLine("Could not determine a winner")
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