package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.CommandException
import com.mrkirby153.botcore.command.args.ArgumentParseException
import com.mrkirby153.botcore.command.slashcommand.dsl.ArgumentConverter
import com.mrkirby153.botcore.command.slashcommand.dsl.Arguments
import com.mrkirby153.botcore.command.slashcommand.dsl.types.ArgumentBuilder
import com.mrkirby153.giveaways.jpa.GiveawayEntity
import com.mrkirby153.giveaways.jpa.GiveawayRepository
import com.mrkirby153.giveaways.utils.log
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.springframework.context.annotation.Configuration

@Configuration
private class CommandTypeConfig(giveawayRepository: GiveawayRepository) {
    init {
        log.info("Initializing command types")
        Companion.giveawayRepository = giveawayRepository
    }

    companion object {
        lateinit var giveawayRepository: GiveawayRepository
    }
}

object GiveawayConverter : ArgumentConverter<GiveawayEntity> {

    override fun convert(input: OptionMapping): GiveawayEntity {
        return CommandTypeConfig.giveawayRepository.getFirstByMessageIdOrSnowflake(input.asString)
            ?: throw ArgumentParseException("Giveaway not found")
    }

    override val type = OptionType.INTEGER
}

fun Arguments.giveaway(body: ArgumentBuilder<GiveawayEntity>.() -> Unit) =
    ArgumentBuilder(this, GiveawayConverter).apply(body)