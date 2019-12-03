package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import net.dv8tion.jda.api.entities.TextChannel;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GiveawayService {


    CompletableFuture<GiveawayEntity> createGiveaway(TextChannel channel, String name, int winners, String endsIn);

    void deleteGiveaway(String messageId);

    List<String> determineWinners(GiveawayEntity giveaway, int amount);
}
