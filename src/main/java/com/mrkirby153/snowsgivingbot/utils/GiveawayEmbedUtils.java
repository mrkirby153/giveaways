package com.mrkirby153.snowsgivingbot.utils;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import me.mrkirby153.kcutils.Time;
import me.mrkirby153.kcutils.Time.TimeUnit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;

import java.awt.Color;
import java.util.Arrays;
import java.util.stream.Collectors;

public class GiveawayEmbedUtils {

    public static Message renderMessage(GiveawayEntity entity) {
        MessageBuilder mb = new MessageBuilder();
        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle(entity.getName());
        eb.setTimestamp(entity.getEndsAt().toInstant());

        StringBuilder descBuilder = new StringBuilder();
        switch (entity.getState()) {
            case RUNNING:
                long timeLeftMs = entity.getEndsAt().getTime() - System.currentTimeMillis();
                String time = Time.formatLong(timeLeftMs, TimeUnit.SECONDS);
                eb.setColor(Color.GREEN);
                descBuilder.append("Click the reaction below to enter!\n\nTime Left: **");
                descBuilder.append(timeLeftMs < 1000 ? "< 1 Second" : time);
                descBuilder.append("**");
                if (entity.getHost() != null) {
                    descBuilder.append("\nHost: <@!").append(entity.getHost()).append(">");
                }
                eb.setDescription(descBuilder.toString());
                eb.setFooter(entity.getWinners() + " " + (entity.getWinners() > 1 ? "winners" : "winner")
                    + " | Ends at");
                break;
            case ENDED:
                eb.setColor(Color.RED);
                if (entity.getFinalWinners().length == 0) {
                    descBuilder.append("Giveaway has ended!\n\nCould not determine a winner :(");
                    if(entity.getHost() != null) {
                        descBuilder.append("\nHost: <@!").append(entity.getHost()).append(">");
                    }
                } else {
                    if (!entity.isSecret()) {
                        String[] winnerIds = entity.getFinalWinners() != null? entity.getFinalWinners() : new String[0];
                        String winners = Arrays.stream(winnerIds)
                            .map(a -> "<@!" + a.trim() + ">").collect(Collectors.joining(" "));
                        if (winners.length() > 1900) {
                            descBuilder.append("Giveaway has ended!");
                        } else {
                            descBuilder.append("Giveaway has ended!\n\n");
                            descBuilder
                                .append(winnerIds.length > 1 ? "**Winners:** " : "**Winner:** ");
                            descBuilder.append(winners);
                        }
                        if (entity.getHost() != null) {
                            descBuilder.append("\nHost: <@!").append(entity.getHost())
                                .append(">");
                        }
                    } else {
                        eb.setDescription("Giveaway has ended!\n\nWinners will be announced soon!");
                    }
                }
                eb.setFooter(entity.getWinners() + " " + (entity.getWinners() > 1 ? "winners" : "winner")
                    + " | Ended at");
                break;
            case ENDING:
                eb.setColor(Color.RED);
                eb.setDescription("Giveaway has ended! Determining winners");
                eb.setFooter(
                    entity.getWinners() + " " + (entity.getWinners() > 1 ? "winners" : "winner")
                        + " | Ended at");
                break;
        }
        eb.setDescription(descBuilder.toString());

        mb.setEmbed(eb.build());
        mb.build();
        return mb.build();
    }
}
