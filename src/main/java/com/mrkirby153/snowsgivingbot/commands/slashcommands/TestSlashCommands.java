package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.snowsgivingbot.services.slashcommands.annotations.CommandOption;
import com.mrkirby153.snowsgivingbot.services.slashcommands.annotations.SlashCommand;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
public class TestSlashCommands {


    @SlashCommand(name = "ping", clearance = 100, description = "Checks the bot's ping")
    public void pingCommand(SlashCommandEvent event) {
        long startTime = System.currentTimeMillis();
        event.deferReply().queue(reply -> reply
            .editOriginal("Pong! " + Time.format(1, System.currentTimeMillis() - startTime))
            .queue());
    }

    @SlashCommand(name = "echo", description = "Responds with the provided message")
    public void testEchoCommand(SlashCommandEvent event,
        @CommandOption("message") String message) {
        if (message == null) {
            event.reply("You didn't specify a message to reply with").queue();
        } else {
            event.reply("Echo: " + message).queue();
        }
    }

    @SlashCommand(name = "sub command", description = "This is a subcommand")
    public void subCommandTest(SlashCommandEvent event,
        @CommandOption("message") @Nonnull String message) {
        event.reply("Pong!").setEphemeral(true).queue();
    }

    @SlashCommand(name = "sub command1 test", description = "sub-sub command 1")
    public void subSubCommandTest(SlashCommandEvent event) {

    }

    @SlashCommand(name = "sub command1 test1", description = "sub-sub command 2")
    public void subSubCommandTest1(SlashCommandEvent event) {

    }
}
