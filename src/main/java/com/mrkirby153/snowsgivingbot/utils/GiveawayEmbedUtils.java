package com.mrkirby153.snowsgivingbot.utils;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GiveawayEmbedUtils {

    public static Message renderMessage(GiveawayEntity entity, SettingService settingService) {
        MessageBuilder mb = new MessageBuilder();
        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle(entity.getName());
        eb.setTimestamp(entity.getEndsAt().toInstant());

        StringBuilder descBuilder = new StringBuilder();
        switch (entity.getState()) {
            case RUNNING:
                eb.setColor(Color.GREEN);
                if (settingService.get(Settings.USE_BUTTONS, entity.getGuildId())) {
                    descBuilder.append("Click the buttons below to enter!");
                } else {
                    descBuilder.append("Click the reaction below to enter!");
                }
                descBuilder.append("\n\nEnds ");
                long endsAt = entity.getEndsAt().getTime() / 1000;
                descBuilder.append(String.format("<t:%d:R>", endsAt));
                if (entity.getHost() != null) {
                    descBuilder.append("\nHost: <@!").append(entity.getHost()).append(">");
                }
                eb.setDescription(descBuilder.toString());
                eb.setFooter(
                    entity.getWinners() + " " + (entity.getWinners() > 1 ? "winners" : "winner")
                        + " | Ends at");
                break;
            case ENDED:
                eb.setColor(Color.RED);
                if (entity.getFinalWinners().length == 0) {
                    descBuilder.append("Giveaway has ended!\n\nCould not determine a winner :(");
                    if (entity.getHost() != null) {
                        descBuilder.append("\nHost: <@!").append(entity.getHost()).append(">");
                    }
                } else {
                    if (!entity.isSecret()) {
                        String[] winnerIds =
                            entity.getFinalWinners() != null ? entity.getFinalWinners()
                                : new String[0];
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
                eb.setFooter(
                    entity.getWinners() + " " + (entity.getWinners() > 1 ? "winners" : "winner")
                        + " | Ended at");
                break;
            case ENDING:
                eb.setColor(Color.RED);
                descBuilder.append("Giveaway has ended! Determining winners");
                eb.setFooter(
                    entity.getWinners() + " " + (entity.getWinners() > 1 ? "winners" : "winner")
                        + " | Ended at");
                break;
        }
        if (settingService.get(Settings.USE_BUTTONS, entity.getGuildId())) {
            setButtons(mb, entity);
        }
        eb.setDescription(descBuilder.toString());

        mb.setEmbed(eb.build());
        mb.build();
        return mb.build();
    }

    private static void setButtons(MessageBuilder builder, GiveawayEntity entity) {
        List<Button> components = new ArrayList<>();
        if (entity.getState() == GiveawayState.RUNNING) {
            components.add(
                Button.primary(String.format("enter:%s", entity.getId()), "Enter Giveaway"));
        }
        components.add(Button.secondary(String.format("check:%s", entity.getId()), "Check Entry"));
        builder.setActionRows(ActionRow.of(components));
    }
}
