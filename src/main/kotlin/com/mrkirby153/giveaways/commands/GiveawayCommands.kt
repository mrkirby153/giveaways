package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.CommandException
import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.command.slashcommand.dsl.slashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.types.optionalInt
import com.mrkirby153.botcore.command.slashcommand.dsl.types.optionalTextChannel
import com.mrkirby153.botcore.command.slashcommand.dsl.types.optionalUser
import com.mrkirby153.botcore.command.slashcommand.dsl.types.string
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.service.GiveawayService
import com.mrkirby153.giveaways.utils.isNumeric
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.TextChannel
import org.springframework.stereotype.Component

private class StartArgs : Arguments() {
    val name by string {
        displayName = "name"
        description = "The name of the giveaway"
    }
    val duration by string {
        displayName = "duration"
        description = "The duration the giveaway will run"
    }
    val channel by optionalTextChannel {
        displayName = "channel"
        description = "The channel to start the giveaway in"
    }
    val winners by optionalInt {
        displayName = "winners"
        description = "The number of winners"
    }
    val host by optionalUser {
        displayName = "host"
        description = "The host of the giveaway (Defaults to you)"
    }
}

private class EndArgs : Arguments() {
    val id by string {
        displayName = "id"
        description = "The id of the giveaway to end"
    }
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
                } else {
                    if (!channel.canTalk()) {
                        fail("Missing send messages permission in ${channel.asMention}")
                    } else {
                        if (!channel.guild.selfMember.hasPermission(
                                channel,
                                Permission.MESSAGE_EMBED_LINKS
                            )
                        ) {
                            fail("Missing embed links permission in ${channel.asMention}")
                        }
                    }
                }
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
                isNumeric(instance.args.id)
            }
            check {
                val args = instance.args
                val giveaway = giveawayService.lookupByIdOrMessageId(args.id)
                if (giveaway == null) {
                    fail("The provided giveaway was not found")
                } else {
                    if (giveaway.state != GiveawayState.RUNNING) {
                        fail("Cannot end a giveaway that is not running")
                    }
                }
            }
            action {
                this.deferReply(true).queue { replyHook ->
                    val giveaway = giveawayService.lookupByIdOrMessageId(args.id)
                        ?: throw CommandException("The provided giveaway was not found")
                    giveawayService.end(giveaway)
                    replyHook.editOriginal("Ended the giveaway ${giveaway.name}").queue()
                }
            }
        }
    }
}