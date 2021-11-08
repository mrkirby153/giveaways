package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.botcore.command.CommandException;
import com.mrkirby153.botcore.command.slashcommand.SlashCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGuildSlashCommands {

    @SlashCommand(name = "remove", description = "No description provided", clearance = 100)
    public void remove(SlashCommandEvent event) {
        log.info("{} removed all guild specific commands from {}", event.getUser(),
            event.getGuild());
        if (event.isFromGuild()) {
            event.getGuild().updateCommands().addCommands()
                .queue(v -> event.reply("Removed guild specific commands!").queue());
        } else {
            throw new CommandException("This can only be executed from a guild");
        }
    }
}
