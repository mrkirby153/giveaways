package com.mrkirby153.giveaways.commands

import com.mrkirby153.botcore.command.slashcommand.dsl.DslCommandExecutor
import com.mrkirby153.botcore.spring.config.EnableBot
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import javax.transaction.Transactional

@EnableBot
@Configuration
class BotConfiguration(
    private val dslCommandExecutor: DslCommandExecutor
) {

    @Transactional
    @EventListener
    fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        dslCommandExecutor.onSlashCommandInteraction(event)
    }

    @Transactional
    @EventListener
    fun onSlashCommandAutocomplete(event: CommandAutoCompleteInteractionEvent) {
        dslCommandExecutor.onCommandAutoCompleteInteraction(event)
    }
}