package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.utils.GiveawayEmbedUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class GiveawayManager implements GiveawayService {

    private static final String TADA = "\uD83C\uDF89";

    private final JDA jda;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;

    private final Object giveawayLock = new Object();

    private final Map<String, GiveawayEntity> entityCache = new HashMap<>();

    @Override
    public CompletableFuture<GiveawayEntity> createGiveaway(TextChannel channel, String name, int winners, String endsIn) {
        CompletableFuture<GiveawayEntity> cf = new CompletableFuture<>();
        GiveawayEntity entity = new GiveawayEntity();
        entity.setName(name);
        entity.setWinners(winners);
        entity.setEndsAt(new Timestamp(System.currentTimeMillis() + Time.INSTANCE.parse(endsIn)));
        channel.sendMessage(GiveawayEmbedUtils.makeInProgressEmbed(entity)).queue(m -> {
            entity.setMessageId(m.getId());
            m.addReaction(TADA).queue();
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
    public List<String> determineWinners(GiveawayEntity giveaway) {
        List<String> winList = new ArrayList<>();
        List<String> allIds = entrantRepository.findAllIdsFromGiveaway(giveaway);
        for (int i = 0; i < giveaway.getWinners(); i++) {
            winList.add(allIds.remove((int) (Math.random() * allIds.size())));
        }
        return winList;
    }

    private void updateGiveaways(Timestamp before) {
        synchronized (giveawayLock) {
            List<GiveawayEntity> giveaways = giveawayRepository.findAllByEndsAtBeforeAndStateIs(before, GiveawayState.RUNNING);
            giveaways.forEach(giveaway -> {
                log.debug("Updating {}", giveaway);
                TextChannel channel = jda.getTextChannelById(giveaway.getChannelId());
                if (channel != null) {
                    channel.retrieveMessageById(giveaway.getMessageId()).queue(msg -> {
                        msg.editMessage(GiveawayEmbedUtils.makeInProgressEmbed(giveaway)).queue();
                    });
                }
            });
        }
    }

    @Scheduled(fixedDelay = 120000) // two minutes
    public void nextHour() {
        updateGiveaways(new Timestamp(Instant.now().plusSeconds(60 * 60).toEpochMilli()));
    }

    @Scheduled(fixedDelay = 15000) // 15 seconds
    public void nextFiveMinutes() {
        updateGiveaways(new Timestamp(Instant.now().plusSeconds(60 * 5).toEpochMilli()));

    }

    @Scheduled(fixedDelay = 1000)  // 1 second
    public void nextFiveSeconds() {
        updateGiveaways(new Timestamp(Instant.now().plusSeconds(5).toEpochMilli()));
    }

    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void updateAllGiveaways() {
        // Hopefully nobody's creating giveaways a year from now
        updateGiveaways(new Timestamp(Instant.now().plus(Duration.ofDays(365)).toEpochMilli()));
    }

    @Scheduled(fixedDelay = 1000)
    public void updateEndedGiveaways() {
        synchronized (giveawayLock) {
            List<GiveawayEntity> endingGiveaways = giveawayRepository.findAllByEndsAtBeforeAndStateIs(new Timestamp(Instant.now().plusSeconds(1).toEpochMilli()), GiveawayState.RUNNING);
            endingGiveaways.forEach(giveaway -> {
                log.info("Ending giveaway {}", giveaway);
                List<String> winners = determineWinners(giveaway);
                TextChannel channel = jda.getTextChannelById(giveaway.getChannelId());

                String winnersAsMention = winners.stream().map(id -> "<@!" + id + ">").collect(Collectors.joining(" "));
                if (channel != null) {
                    channel.sendMessage(":tada: Congratulations " + winnersAsMention + " you won **" + giveaway.getName() + "**").queue();
                    channel.retrieveMessageById(giveaway.getMessageId()).queue(msg -> {
                        msg.editMessage(GiveawayEmbedUtils.makeEndedEmbed(giveaway, winners)).queue();
                    });
                }

                giveaway.setState(GiveawayState.ENDED);
                giveawayRepository.save(giveaway);
                entityCache.remove(giveaway.getMessageId());
            });
        }
    }

    @EventListener
    public void onReactionAdd(GuildMessageReactionAddEvent event) {
        if (event.getUser().isBot() || event.getUser().isFake()) {
            return; // Ignore bots and fake users
        }
        if (event.getReactionEmote().isEmoji() && event.getReactionEmote().getEmoji().equals(TADA)) {
            GiveawayEntity cached = entityCache.get(event.getMessageId());
            if (cached == null) {
                log.debug("Cache miss. Looking up giveaway entity");
                cached = giveawayRepository.findByMessageId(event.getMessageId())
                        .orElseThrow(() -> new IllegalStateException("Could not find giveaway entity for message " +
                                event.getMessageId()));
                entityCache.put(event.getMessageId(), cached);
            }
            enterGiveaway(event.getUser(), cached);
        }
    }

    private void enterGiveaway(User user, GiveawayEntity entity) {
        if (entrantRepository.existsByGiveawayAndUserId(entity, user.getId())) {
            log.debug("{} has already entered {}", user, entity);
        } else {
            if (entity.getState() != GiveawayState.RUNNING) {
                log.debug("Not entering {} into {}. Has already ended", user, entity);
            }
            log.debug("Entering {} into {}", user, entity);
            GiveawayEntrantEntity gee = new GiveawayEntrantEntity(entity, user.getId());
            entrantRepository.save(gee);
        }
    }
}
