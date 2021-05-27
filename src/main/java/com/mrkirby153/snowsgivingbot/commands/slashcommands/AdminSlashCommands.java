package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.botcore.command.slashcommand.SlashCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSlashCommands {

    @SlashCommand(name = "ping", description = "Checks the bot's ping")
    public void ping(SlashCommandEvent event) {
        long start = System.currentTimeMillis();
        event.deferReply(true).queue(hook -> {
            hook.editOriginal("Pong! " + Time.format(1, System.currentTimeMillis() - start))
                .queue();
        });
    }
}
