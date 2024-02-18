package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.spring.argument
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.command.slashcommand.dsl.types.textChannel
import com.mrkirby153.botcore.command.slashcommand.dsl.types.user
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.botcore.utils.SLF4J
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.service.GiveawayService
import com.mrkirby153.giveaways.utils.canSee
import com.mrkirby153.giveaways.utils.effectiveUsername
import com.mrkirby153.interactionmenus.Menu
import com.mrkirby153.interactionmenus.MenuManager
import com.mrkirby153.interactionmenus.StatefulMenu
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.sharding.ShardManager
import org.springframework.stereotype.Component
import kotlin.math.ceil
import kotlin.math.min


@Component
class GiveawayCommands(
    private val shardManager: ShardManager,
    private val giveawayService: GiveawayService,
    private val giveawayRepository: GiveawayRepository,
    private val menuManager: MenuManager
) : DslSlashCommandProvider {


    private val log by SLF4J

    override fun register(executor: DslCommandExecutor) {
        executor.registerCommands {
            slashCommand("start") {
                description = "Starts a giveaway"

                val name by string {
                    description = "The name of the giveaway"
                }.required()

                val duration by duration {
                    description = "The duration of the giveaway"
                }.required()

                val channel by textChannel {
                    description = "The channel to start the giveaway in"
                }.optional()

                val winners by int {
                    min = 1
                    description = "The number of winners"
                }.optional(1)

                val host by user {
                    description = "The user hosting the giveaway"
                }.optional()

                run {
                    val realChannel = channel() ?: (this.channel as? TextChannel)
                    if (realChannel == null) {
                        throw CommandException("This command can only be used in servers")
                    }
                    if (!realChannel.canTalk()) {
                        throw CommandException("Unable to send messages in ${realChannel.asMention}: Missing permissions")
                    }

                    val hook = deferReply(true).await()
                    giveawayService.start(
                        realChannel,
                        name(),
                        System.currentTimeMillis() + duration(),
                        winners(),
                        host() ?: this.user
                    )
                    hook.editOriginal("Started giveaway").await()
                }
            }
            slashCommand("end") {
                description = "Ends a giveaway"

                val giveaway by giveawayRepository.argument {
                    description = "The giveaway to end"
                }.required()

                run {
                    if (giveaway().state != GiveawayState.RUNNING) {
                        throw CommandException("Cannot end a giveaway that is not running")
                    }
                    val channel = guild?.getTextChannelById(giveaway().channelId)
                        ?: throw CommandException("Giveaway not found")
                    if (!this.user.canSee(channel)) {
                        throw CommandException("Giveaway not found")
                    }
                    giveawayService.end(giveaway())
                    reply(true) {
                        content = "Ended the giveaway ${giveaway().name}"
                    }.await()
                }
            }

            slashCommand("reroll") {
                description = "Rerolls the winners"

                val giveaway by giveawayRepository.argument {
                    description = "The giveaway to reroll"
                }.required()

                run {
                    if (giveaway().state != GiveawayState.ENDED) {
                        throw CommandException("Cannot reroll a giveaway that hasn't ended yet")
                    }
                    val channel = guild?.getTextChannelById(giveaway().channelId)
                        ?: throw CommandException("Giveaway not found")
                    if (!this.user.canSee(channel)) {
                        throw CommandException("Giveaway not found")
                    }
                    val hook = deferReply(true).await()
                    val menu = buildRerollMenu(giveaway())
                    menuManager.show(menu, hook).await()
                    println("Done!")
                }
            }
        }
    }

    enum class RerollMenuPages {
        SELECT, CONFIRM, RESULT
    }

    private data class RerollMenuState(
        var userPage: Int = 0, val selectedUsers: MutableList<String> = mutableListOf()
    )

    private suspend fun buildRerollMenu(giveaway: GiveawayEntity): Menu<RerollMenuPages> {
        val winners: MutableMap<String, User?> = mutableMapOf()

        giveaway.getWinners().forEach { id ->
            log.trace("Retrieving user $id")
            try {
                winners[id] = shardManager.retrieveUserById(id).await()
            } catch (e: ErrorResponseException) {
                if (e.errorResponse == ErrorResponse.UNKNOWN_USER) {
                    winners[id] = null
                } else {
                    throw e
                }
            }
            log.trace("Retrieved user {} -> {}", id, winners[id])
        }

        val totalPages = ceil(giveaway.getWinners().size / 25.0)
        val singleWinner = giveaway.winners == 1

        val rerollState = if (singleWinner) {
            RerollMenuState(selectedUsers = giveaway.getWinners().toMutableList())
        } else {
            RerollMenuState()
        }

        return StatefulMenu(
            if (singleWinner) RerollMenuPages.CONFIRM else RerollMenuPages.SELECT,
            rerollState
        ) {
            page(RerollMenuPages.SELECT) {
                val start = state.userPage * 25
                val end = min(start + 24, giveaway.getWinners().size - 1)
                val users = giveaway.getWinners().slice(start..end)
                text {
                    appendLine("Select the users to re-roll")
                    appendLine()
                    if (state.selectedUsers.isNotEmpty()) {
                        appendLine("**Selected**")
                        state.selectedUsers.forEach { userId ->
                            val winner = winners[userId]
                            val name = if (winner != null) {
                                "${winner.asMention} (`${winner.id}`)"
                            } else {
                                "`${userId}`"
                            }
                            appendLine("- $name")
                        }
                    }
                }

                actionRow {
                    stringSelect {
                        max = min(giveaway.winners, 25)
                        min = 0
                        // Build the select page
                        users.forEach { id ->
                            val winner = winners[id]
                            val name = if (winner != null) {
                                "${winner.effectiveUsername} (${winner.id})"
                            } else {
                                id
                            }
                            option(name, id) {
                                default = id in state.selectedUsers
                            }
                        }
                        onChange { _, selected ->
                            val selectedIds = selected.map { it.value }
                            users.forEach { original ->
                                if (original in selectedIds && original !in state.selectedUsers) {
                                    log.trace("Adding $original")
                                    state.selectedUsers.add(original)
                                } else {
                                    if (original in state.selectedUsers && original !in selectedIds) {
                                        log.trace("Removing $original")
                                        state.selectedUsers.remove(original)
                                    }
                                }
                            }
                            markDirty()
                        }
                    }

                }
                actionRow {
                    button("") {
                        enabled = state.userPage > 0
                        emoji = Emoji.fromUnicode("⬅\uFE0F")

                        onClick {
                            state.userPage -= 1
                            markDirty()
                        }
                    }
                    button("") {
                        style = ButtonStyle.SUCCESS
                        emoji = Emoji.fromUnicode("✅")
                        onClick {
                            currentPage = RerollMenuPages.CONFIRM
                        }
                    }
                    button("") {
                        enabled = state.userPage + 1 < totalPages
                        emoji = Emoji.fromUnicode("➡\uFE0F")
                        onClick {
                            state.userPage += 1
                            markDirty()
                        }
                    }

                }
            }
            page(RerollMenuPages.CONFIRM) {
                text {
                    appendLine("Are you sure you want to re-roll `${giveaway.name}`? This will re-roll the following users:")
                    state.selectedUsers.forEach { userId ->
                        val winner = winners[userId]
                        val name = if (winner != null) {
                            "${winner.asMention} (`${winner.id}`)"
                        } else {
                            "`${userId}`"
                        }
                        appendLine("- $name")
                    }
                }
                actionRow {
                    if (!singleWinner) {
                        button("") {
                            emoji = Emoji.fromUnicode("❌")
                            onClick {
                                currentPage = RerollMenuPages.SELECT
                            }
                        }
                    }
                    button("") {
                        style = ButtonStyle.SUCCESS
                        emoji = Emoji.fromUnicode("✅")
                        onClick {
                            giveawayService.rerollGiveaway(giveaway, state.selectedUsers)
                            currentPage = RerollMenuPages.RESULT
                        }
                    }
                }
            }

            page(RerollMenuPages.RESULT) {
                text {
                    appendLine("Re-rolling `${giveaway.name}`!")
                }
            }
        }
    }
}