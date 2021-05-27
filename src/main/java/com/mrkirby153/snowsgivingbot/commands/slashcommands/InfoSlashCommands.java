package com.mrkirby153.snowsgivingbot.commands.slashcommands;

import com.mrkirby153.botcore.command.slashcommand.SlashCommand;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;

import static com.mrkirby153.snowsgivingbot.commands.HelpCommand.DEFAULT_PERMISSIONS;
import static com.mrkirby153.snowsgivingbot.commands.HelpCommand.DISCORD_OAUTH_INVITE;

@Component
@Slf4j
public class InfoSlashCommands {

    private final ShardManager shardManager;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;
    private final String prefix;
    private final String permissions;
    private final String supportServer;

    public InfoSlashCommands(@Value("${bot.prefix:!}") String prefix,
        @Value("${bot.support-server:}") String supportServer,
        @Value("${bot.permissions:" + DEFAULT_PERMISSIONS + "}") String permissions,
        ShardManager shardManager,
        GiveawayRepository giveawayRepository, EntrantRepository entrantRepository) {
        this.shardManager = shardManager;
        this.prefix = prefix;
        this.supportServer = supportServer;
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
        this.permissions = permissions;
    }


    @SlashCommand(name = "info", description = "Displays information about the bot")
    public void info(SlashCommandEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        String botName = event.getJDA().getSelfUser().getName();
        eb.setTitle(botName + " Statistics");
        eb.setColor(Color.BLUE);
        eb.setDescription(botName
            + " lets you quickly and easily run giveaways on your server. For help getting started, use `"
            + prefix + "help`" + (!supportServer.equals("") ? " or join the [support server]("
            + supportServer + ")." : "."));
        eb.addField("Guilds", Integer.toString(shardManager.getGuilds().size()), true);
        eb.addField("Shards",
            "[" + event.getJDA().getShardInfo().getShardId() + " / " + event.getJDA()
                .getShardInfo().getShardTotal() + "]", true);
        eb.addField("Giveaways Held", Long.toString(giveawayRepository.count()), true);
        eb.addField("Entrants Recorded", Long.toString(entrantRepository.count()), true);
        event.replyEmbeds(eb.build()).queue();
    }

    @SlashCommand(name = "invite", description = "Gets an invite for the bot")
    public void invite(SlashCommandEvent event) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(String.format("%s Invite", event.getJDA().getSelfUser().getName()));
        builder.setDescription("You can invite me to your server by clicking [here](" + String
            .format(DISCORD_OAUTH_INVITE, event.getJDA().getSelfUser().getId(), permissions)
            + "). \n\nBy default only users with **Manage Server** can start giveaways, but this can be changed. See **"
            + prefix + "help** for more information");
        builder.setColor(Color.BLUE);
        event.replyEmbeds(builder.build()).setEphemeral(true).queue();
    }
}
