package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GiveawayService {


    CompletableFuture<GiveawayEntity> createGiveaway(TextChannel channel, String name, int winners, String endsIn, boolean secret);

    void deleteGiveaway(String messageId);

    List<String> determineWinners(GiveawayEntity giveaway);

    void endGiveaway(String messageId);

    void reroll(String mid, String[] users);

    List<GiveawayEntity> getAllGiveaways(Guild guild);
}
