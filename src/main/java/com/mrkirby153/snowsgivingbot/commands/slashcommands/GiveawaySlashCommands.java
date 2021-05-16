package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.ConfirmationService;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import com.mrkirby153.snowsgivingbot.services.PermissionService;
import com.mrkirby153.snowsgivingbot.services.slashcommands.annotations.CommandOption;
import com.mrkirby153.snowsgivingbot.services.slashcommands.annotations.SlashCommand;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

@Component
@AllArgsConstructor
@Slf4j
public class GiveawaySlashCommands {

    private final GiveawayService giveawayService;
    private final EntrantRepository er;
    private final GiveawayRepository gr;
    private final PermissionService ps;
    private final ConfirmationService confirmationService;
    private final ShardManager shardManager;

    @SlashCommand(name = "start", description = "Starts a new giveaway")
    public void start(SlashCommandEvent event,
        @CommandOption(value = "duration", description = "How long the giveaway is to last (i.e. 3h)") @Nonnull String duration,
        @CommandOption(value = "prize", description = "The prize to give away") @Nonnull String prize,
        @CommandOption(value = "winners", description = "The amount of winners (default 1)") Integer winners,
        @CommandOption(value = "channel", description = "The channel to run the giveaway in (defaults to the current channel)")
            TextChannel textChannel,
        @CommandOption(value = "host", description = "The host of the giveaway (defaults to you)")
            User host) {
        if (winners == null) {
            winners = 1;
        }
        if (textChannel == null) {
            textChannel = event.getTextChannel();
        }
        if (host == null) {
            host = event.getUser();
        }
        if (!event.getGuild().getSelfMember()
            .hasPermission(textChannel, Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_EMBED_LINKS)) {
            event.reply(":no_entry: I can't start a giveaway in " + textChannel.getAsMention()
                + " due to missing permissions. Ensure that I have permissions to add reactions and embed links!")
                .setEphemeral(true).queue();
            return;
        }
        try {
            giveawayService.createGiveaway(textChannel, prize, winners, duration, false, host);
            event.reply("Giveaway has been created in " + textChannel.getAsMention())
                .setEphemeral(true).queue();
        } catch (IllegalArgumentException e) {
            event.reply(":no_entry: " + e.getMessage()).setEphemeral(true).queue();
        }
    }
}
