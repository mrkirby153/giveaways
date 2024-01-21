package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.AbstractSlashCommand
import com.mrkirby153.botcore.command.slashcommand.dsl.ArgumentConverter
import com.mrkirby153.botcore.command.slashcommand.dsl.ArgumentParseException
import com.mrkirby153.botcore.command.slashcommand.dsl.types.SimpleArgumentBuilder
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType

object DurationConverter : ArgumentConverter<Long> {
    override fun convert(input: OptionMapping): Long {
        try {
            return Time.parse(input.asString)
        } catch (e: IllegalArgumentException) {
            throw ArgumentParseException(e.message ?: "An unknown error occurred")
        }
    }

    override val type: OptionType = OptionType.STRING
}

fun AbstractSlashCommand.duration(
    name: String? = null,
    body: SimpleArgumentBuilder<Long>.() -> Unit = {}
) =
    SimpleArgumentBuilder(this, DurationConverter).apply {
        if (name != null) this@apply.name = name
    }.apply(body)