package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.args.ArgumentParseException
import com.mrkirby153.botcore.command.slashcommand.dsl.ArgumentConverter
import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.types.ArgumentBuilder
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import me.mrkirby153.kcutils.Time
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.springframework.stereotype.Service

@Service
class CommandTypes(
    private val repository: GiveawayRepository
) {
    inner class GiveawayConverter : ArgumentConverter<GiveawayEntity> {
        override fun convert(input: OptionMapping): GiveawayEntity {
            return repository.getFirstByMessageIdOrSnowflake(input.asString)
                ?: throw ArgumentParseException("Giveaway not found")
        }
    }

    private val giveawayConverter = GiveawayConverter()

    fun giveaway(arguments: Arguments, body: ArgumentBuilder<GiveawayEntity>.() -> Unit) =
        ArgumentBuilder(arguments, giveawayConverter).apply(body)
}

object DurationConverter : ArgumentConverter<Long> {
    override fun convert(input: OptionMapping): Long {
        try {
            return Time.parse(input.asString)
        } catch (e: IllegalArgumentException) {
            throw ArgumentParseException(e.message ?: "An unknown error occurred")
        }
    }

    override val type = OptionType.STRING

}

fun Arguments.duration(body: ArgumentBuilder<Long>.() -> Unit) =
    ArgumentBuilder(this, DurationConverter).apply(body)