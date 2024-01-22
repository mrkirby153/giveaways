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
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.jpa.GiveawayState
import com.mrkirby153.giveaways.service.AmqpService
import com.mrkirby153.giveaways.service.GiveawayService
import com.mrkirby153.giveaways.utils.canSee
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Component


@Component
class GiveawayCommands(
    private val giveawayService: GiveawayService,
    private val giveawayRepository: GiveawayRepository,
    private val amqpService: AmqpService
) : DslSlashCommandProvider {

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
        }
    }
}