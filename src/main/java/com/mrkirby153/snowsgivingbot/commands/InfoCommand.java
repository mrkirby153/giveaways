package com.mrkirby153.snowsgivingbot.commands;

import com.mrkirby153.botcore.command.Command;
import com.mrkirby153.botcore.command.Context;
import com.mrkirby153.botcore.command.args.CommandContext;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;

@Component
public class InfoCommand {

    private final ShardManager shardManager;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;
    private final String prefix;
    private final String supportServer;

    public InfoCommand(@Value("${bot.prefix:!}") String prefix,
        @Value("${bot.support-server:}") String supportServer, ShardManager shardManager,
        GiveawayRepository giveawayRepository, EntrantRepository entrantRepository) {
        this.shardManager = shardManager;
        this.prefix = prefix;
        this.supportServer = supportServer;
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
    }


    @Command(name = "info")
    public void info(Context context, CommandContext commandContext) {
        EmbedBuilder eb = new EmbedBuilder();
        String botName = context.getJDA().getSelfUser().getName();
        eb.setTitle(botName + " Statistics");
        eb.setColor(Color.BLUE);
        eb.setDescription(botName
            + " lets you quickly and easily run giveaways on your server. For help getting started, use `"
            + prefix + "help`" + (!supportServer.equals("") ? " or join the [support server]("
            + supportServer + ")." : "."));
        eb.addField("Guilds", Integer.toString(shardManager.getGuilds().size()), true);
        eb.addField("Shards",
            "[" + context.getJDA().getShardInfo().getShardId() + " / " + context.getJDA()
                .getShardInfo().getShardTotal() + "]", true);
        eb.addField("Giveaways Held", Long.toString(giveawayRepository.count()), true);
        eb.addField("Entrants Recorded", Long.toString(entrantRepository.count()), true);
        context.getChannel().sendMessage(eb.build()).queue();
    }
}
