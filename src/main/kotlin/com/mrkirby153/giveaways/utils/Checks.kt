package com.mrkirby153.giveaways.utils

import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.utils.PrerequisiteCheck
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel

fun PrerequisiteCheck<out Arguments>.isNumeric(string: String) {
    failWhen(string.toDoubleOrNull() == null, "`$string` is not a number")
}


fun User.canSee(channel: GuildChannel): Boolean {
    val member = channel.guild.getMember(this) ?: return false
    return member.hasPermission(channel, Permission.VIEW_CHANNEL)
}