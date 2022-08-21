package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.int
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.botcore.command.slashcommand.dsl.types.textChannel
import com.mrkirby153.botcore.command.slashcommand.dsl.types.user
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.service.GiveawayService
import com.mrkirby153.giveaways.utils.canSeeGiveaway
import com.mrkirby153.giveaways.utils.canTalk
import com.mrkirby153.giveaways.utils.giveawayIsInState
import com.mrkirby153.giveaways.utils.requirePermissions
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.TextChannel
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
    }.optional()
    val host by user {
        name = "host"
        description = "The host of the giveaway (Defaults to you)"
    }.optional()
}

private class EndArgs : Arguments() {
    val giveaway by giveaway {
        name = "id"
        description = "Either the id or message id of the giveaway to end"
    }.required()
}


@Component
class GiveawayCommands(
    private val giveawayService: GiveawayService
) : DslSlashCommandProvider {
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
                    giveawayService.start(
                        channel,
                        args.name,
                        System.currentTimeMillis() + duration,
                        args.winners ?: 1,
                        args.host ?: this.user
                    ).thenAccept {
                        replyHook.editOriginal("Started giveaway in ${channel.asMention}").queue()
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