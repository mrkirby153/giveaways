package com.mrkirby153.giveaways.utils

import com.mrkirby153.botcore.command.slashcommand.dsl.AbstractSlashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.CommandPrerequisiteCheck
import com.mrkirby153.botcore.utils.PrerequisiteCheck
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayState
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.MessageChannel

fun PrerequisiteCheck<out Arguments>.isNumeric(string: String) {
    failWhen(string.toDoubleOrNull() == null, "`$string` is not a number")
}

fun CommandPrerequisiteCheck<out Arguments>.giveawayIsInState(
    giveawayEntity: GiveawayEntity,
    state: GiveawayState,
    message: String?
) {
    failWhen(giveawayEntity.state != state, message)
}

fun CommandPrerequisiteCheck<out Arguments>.canSeeGiveaway(
    giveawayEntity: GiveawayEntity
) {
    val guild = this.instance.guild
    val member = this.instance.member
    val channel = guild?.getTextChannelById(giveawayEntity.channelId)
    if (guild == null || member == null || channel == null) {
        fail("Giveaway not found")
        return
    }
    failWhen(!member.hasPermission(channel, Permission.VIEW_CHANNEL), "Giveaway not found")
}

fun CommandPrerequisiteCheck<out Arguments>.requirePermissions(
    channel: GuildChannel,
    vararg permission: Permission
) {
    val missingPermissions =
        permission.filter { this.instance.member?.hasPermission(channel, it) == false }
    failWhen(
        missingPermissions.isNotEmpty(),
        "Missing the following permissions in ${channel.asMention}: ${
            missingPermissions.joinToString(",") {
                it.name
            }
        }"
    )
}

fun CommandPrerequisiteCheck<out Arguments>.canTalk(channel: GuildChannel) {
    if (channel !is MessageChannel) {
        fail("Unable to send messages in ${channel.asMention}: Not a message channel")
        return
    }
    failWhen(
        !channel.canTalk(),
        "Unable to send messages in ${(channel as MessageChannel).asMention}: Missing permissions"
    )
}

fun AbstractSlashCommand<out Arguments>.requirePermissions(
    channel: GuildChannel,
    vararg permission: Permission
) {
    this.check {
        requirePermissions(channel, *permission)
    }
}