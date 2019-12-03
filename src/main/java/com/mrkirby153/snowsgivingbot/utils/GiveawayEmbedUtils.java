package com.mrkirby153.snowsgivingbot.utils;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;


public class GiveawayEmbedUtils {


    public static MessageEmbed makeInProgressEmbed(GiveawayEntity entity) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(entity.getName());
        long timeLeftMs = entity.getEndsAt().getTime() - System.currentTimeMillis();
        builder.setDescription("Click the reaction below to enter! \n\n Time Left: **" + Time.INSTANCE.formatLong(timeLeftMs, Time.TimeUnit.SECONDS));
        builder.setColor(timeLeftMs > 120000 ? Color.green : Color.red);
        builder.setFooter(entity.getWinners() + " winners | Ends at");
        builder.setTimestamp(entity.getEndsAt().toInstant());
        return builder.build();
    }

    public static MessageEmbed makeEndedEmbed(GiveawayEntity entity, List<String> winners) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(entity.getName());
        builder.setDescription("Giveaway has ended! \n\n Winners: " + winners.stream().map(id -> "<@!" + id + ">").collect(Collectors.joining(" ")));
        builder.setColor(Color.RED);
        builder.setFooter(entity.getWinners() + " winners | Ended at");
        builder.setTimestamp(entity.getEndsAt().toInstant());
        return builder.build();
    }
}
