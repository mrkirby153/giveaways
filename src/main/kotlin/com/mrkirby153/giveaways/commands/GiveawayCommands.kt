package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.command.slashcommand.dsl.types.textChannel
import com.mrkirby153.botcore.command.slashcommand.dsl.types.user
import com.mrkirby153.botcore.coroutine.await
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.service.GiveawayService
import com.mrkirby153.giveaways.utils.canSeeGiveaway
import com.mrkirby153.giveaways.utils.canTalk
import com.mrkirby153.giveaways.utils.giveawayIsInState
import com.mrkirby153.giveaways.utils.requirePermissions
import kotlinx.coroutines.runBlocking
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Component

private class StartArgs : Arguments() {
    val name by string {
        name = "name"
        description = "The name of the giveaway"
    }.required()
    val duration by string {
        name = "duration"
        description = "The duration the giveaway will run"
    }.required()
    val channel by textChannel {
        name = "channel"
        description = "The channel to start the giveaway in"
    }.optional()
    val winners by int {
        name = "winners"
        description = "The number of winners"
    }.optional(1)
    val host by user {
        name = "host"
        description = "The host of the giveaway (Defaults to you)"
    }.optional()
}


@Component
class GiveawayCommands(
    private val giveawayService: GiveawayService,
    private val commandTypes: CommandTypes
) : DslSlashCommandProvider {

    private inner class EndArgs : Arguments() {
        val giveaway by commandTypes.giveaway(this) {
            name = "id"
            description = "Either the id or message id of the giveaway to end"
        }.required()
    }

    override fun register(executor: DslCommandExecutor) {
        executor.slashCommand(::StartArgs) {
            name = "start"
            description = "Starts a giveaway"
            check {
                val channel = instance.args.channel ?: (instance.channel as? TextChannel)
                if (channel == null) {
                    fail("This command can only be used in servers!")
                    return@check
                }
                canTalk(channel)
            }
            check {
                val channel = instance.args.channel ?: (instance.channel as? TextChannel)!!
                requirePermissions(channel, Permission.MESSAGE_EMBED_LINKS)
            }
            action {
                this.deferReply(true).queue { replyHook ->
                    val channel = args.channel ?: this.channel as TextChannel
                    val duration = try {
                        Time.parse(args.duration)
                    } catch (e: IllegalArgumentException) {
                        throw CommandException("Could not parse the provided duration `${args.duration}`")
                    }
                    runBlocking {
                        giveawayService.start(
                            channel,
                            args.name,
                            System.currentTimeMillis() + duration,
                            args.winners,
                            args.host ?: this@action.user
                        )
                        replyHook.editOriginal("Started giveaway in ${channel.asMention}").await()
                    }
                }
            }
        }

        executor.slashCommand(::EndArgs) {
            name = "end"
            description = "Ends a giveaway"
            check {
                giveawayIsInState(
                    args.giveaway,
                    GiveawayState.RUNNING,
                    "Cannot end a giveaway that is not running"
                )
                canSeeGiveaway(args.giveaway)
            }
            action {
                this.deferReply(true).queue { replyHook ->
                    giveawayService.end(args.giveaway)
                    replyHook.editOriginal("Ended the giveaway ${args.giveaway.name}").queue()
                }
            }
        }
    }
}