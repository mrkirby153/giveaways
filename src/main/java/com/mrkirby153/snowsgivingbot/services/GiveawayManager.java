package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.utils.GiveawayEmbedUtils;
import lombok.AllArgsConstructor;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
public class GiveawayManager implements GiveawayService {

    private final JDA jda;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;

    private final Map<String, GiveawayEntity> giveawayCache = new HashMap<>();


    @Override
    public CompletableFuture<GiveawayEntity> createGiveaway(TextChannel channel, String name, int winners, String endsIn) {
        CompletableFuture<GiveawayEntity> cf = new CompletableFuture<>();
        GiveawayEntity entity = new GiveawayEntity();
        entity.setName(name);
        entity.setWinners(winners);
        entity.setEndsAt(new Timestamp(System.currentTimeMillis() + Time.INSTANCE.parse(endsIn)));
        channel.sendMessage(GiveawayEmbedUtils.makeInProgressEmbed(entity)).queue(m -> {
            entity.setMessageId(m.getId());
            cf.complete(giveawayRepository.save(entity));
        });
        return cf;
    }

    @Override
    public void deleteGiveaway(String messageId) {
        GiveawayEntity entity = giveawayRepository.findByMessageId(messageId).orElseThrow(() -> new IllegalArgumentException("Giveaway not found"));
        TextChannel c = jda.getTextChannelById(entity.getChannelId());
        if (c != null) {
            c.deleteMessageById(entity.getMessageId()).queue();
        }
        giveawayRepository.delete(entity);
    }

    @Override
    public List<String> determineWinners(GiveawayEntity giveaway, int amount) {
        List<String> winList = new ArrayList<>();
        List<String> allIds = entrantRepository.findAllIdsFromGiveaway(giveaway);
        for (int i = 0; i < amount; i++) {
            winList.add(allIds.remove((int) (Math.random() * allIds.size())));
        }
        return winList;
    }

    @Scheduled(fixedDelay = 60000)
    public void updateGiveawayEmbeds() {
        giveawayCache.forEach((message, giveaway) -> {
            TextChannel c = jda.getTextChannelById(giveaway.getChannelId());
            if (c != null) {
                c.retrieveMessageById(message).queue(msg -> {
                    msg.editMessage(GiveawayEmbedUtils.makeInProgressEmbed(giveaway)).queue();
                });
            }
        });
    }

    // TODO: 2019-12-03 As the giveaway is closer to ending, it should update more frequently (Like every 10 seconds)
    // TODO: 2019-12-03 Do the winning stuff
}
