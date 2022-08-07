package com.mrkirby153.giveaways.service

import com.mrkirby153.botcore.builder.message
import com.mrkirby153.giveaways.events.GiveawayEndingEvent
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.utils.pluralize
import net.dv8tion.jda.api.entities.Message
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.awt.Color

interface GiveawayMessageService {
    fun render(giveawayEntity: GiveawayEntity): Message
}

@Service
class GiveawayMessageManager : GiveawayMessageService {

    override fun render(giveawayEntity: GiveawayEntity): Message {
        return when (giveawayEntity.state) {
            GiveawayState.RUNNING -> renderRunning(giveawayEntity)
            GiveawayState.ENDED -> renderEnded(giveawayEntity)
            GiveawayState.ENDING -> renderEnding(giveawayEntity)
        }
    }

    @EventListener
    fun onEnding(event: GiveawayEndingEvent) {

    }

    private fun renderRunning(entity: GiveawayEntity): Message = message {
        embed {
            color {
                color = Color.GREEN
            }
            description = buildString {
                appendLine("Click the buttons below to enter!")
                val endsAt = entity.endsAt.time / 1000
                appendLine()
                appendLine("Ends <t:$endsAt:R>")
                if (entity.host != null) {
                    appendLine("Hosted by <@!${entity.host}>")
                }
            }
            footer {
                text = "${entity.id} | ${pluralize(entity.winners, "winner", "winners")}"
            }
        }
    }

    private fun renderEnded(entity: GiveawayEntity): Message = message {
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
            footer {
                text = "${entity.id} | ${pluralize(entity.winners, "winner", "winners")}"
            }
        }
    }

    private fun renderEnding(entity: GiveawayEntity): Message = message {
        embed {
            color {
                color = Color.RED
            }
            description = buildString {
                appendLine("Giveaway has ended!")
                appendLine()
                appendLine("Determining winners...")
            }
            footer {
                text = "${entity.id} | ${pluralize(entity.winners, "winner", "winners")}"
            }
        }
    }
}