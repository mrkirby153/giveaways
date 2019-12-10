package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.utils.GiveawayEmbedUtils;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.transaction.Transactional;

@Service
@Slf4j
public class GiveawayManager implements GiveawayService {

    private static final String TADA = "\uD83C\uDF89";

    private final JDA jda;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;
    private final DiscordService discordService;

    private final Object giveawayLock = new Object();

    private final Map<String, GiveawayEntity> entityCache = new HashMap<>();

    private final AtomicLong counter = new AtomicLong(0);

    private final String emoji;
    private final boolean custom;
    private final String emoteId;

    public GiveawayManager(JDA jda, EntrantRepository entrantRepository,
        GiveawayRepository giveawayRepository,
        DiscordService discordService,
        @Value("${bot.reaction:\uD83C\uDF89}") String emote) {
        this.jda = jda;
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
        this.discordService = discordService;

        if (emote.matches("\\d{17,18}")) {
            emoji = null;
            custom = true;
            emoteId = emote;
        } else {
            emoji = emote;
            custom = false;
            emoteId = null;
        }
    }

    @Override
    public CompletableFuture<GiveawayEntity> createGiveaway(TextChannel channel, String name,
        int winners, String endsIn, boolean secret) {
        CompletableFuture<GiveawayEntity> cf = new CompletableFuture<>();
        GiveawayEntity entity = new GiveawayEntity();
        entity.setName(name);
        entity.setWinners(winners);
        entity.setEndsAt(new Timestamp(System.currentTimeMillis() + Time.INSTANCE.parse(endsIn)));
        entity.setChannelId(channel.getId());
        entity.setSecret(secret);
        channel.sendMessage(GiveawayEmbedUtils.renderMessage(entity)).queue(m -> {
            entity.setMessageId(m.getId());
            if (custom) {
                m.addReaction(discordService.findEmoteById(emoteId)).queue();
            } else {
                m.addReaction(emoji).queue();
            }
            cf.complete(giveawayRepository.save(entity));
        });
        return cf;
    }

    @Override
    public void deleteGiveaway(String messageId) {
        GiveawayEntity entity = giveawayRepository.findByMessageId(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Giveaway not found"));
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
            if (allIds.isEmpty()) {
                break; // Break if we've selected everyone
            }
            winList.add(allIds.remove((int) (Math.random() * allIds.size())));
        }
        return winList;
    }

    @Override
    public void endGiveaway(String messageId) {
        GiveawayEntity ge = giveawayRepository.findByMessageId(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Giveaway not found"));
        ge.setEndsAt(new Timestamp(System.currentTimeMillis()));
        giveawayRepository.save(ge);
    }

    @Override
    public void reroll(String mid) {
        GiveawayEntity ge = giveawayRepository.findByMessageId(mid)
            .orElseThrow(() -> new IllegalArgumentException("Giveaway not found"));
        if (ge.getState() != GiveawayState.ENDED) {
            throw new IllegalArgumentException("Cannot reroll an in progress giveaway");
        }
        endGiveaway(ge);
    }

    private void updateGiveaways(Timestamp before) {
        synchronized (giveawayLock) {
            List<GiveawayEntity> giveaways = giveawayRepository
                .findAllByEndsAtBeforeAndStateIs(before, GiveawayState.RUNNING);
            updateMultipleGiveaways(giveaways);
        }
    }


    @Scheduled(fixedDelay = 1000L) // 1 Second
    public void updateGiveaways() {
        updateEndedGiveaways();
        long count = counter.getAndIncrement();
        Instant now = Instant.now();
        if (count % 120 == 0) {
            // Update giveaways ending in an hour every two minutes
            updateGiveaways(new Timestamp(now.plusSeconds(60 * 60).toEpochMilli()));
        } else if (count % 15 == 0) {
            // Update giveaways ending in 5 minutes every 15 seconds
            updateGiveaways(new Timestamp(now.plusSeconds(60 * 5).toEpochMilli()));
        } else {
            // Update giveaways ending in 5 seconds every second
            updateGiveaways(new Timestamp(now.plusSeconds(5).toEpochMilli()));
        }
    }

    @Scheduled(fixedDelay = 120000L) // 2 minutes
    public void updateAllGiveaways() {
        log.debug("Updating all giveaways");
        List<GiveawayEntity> activeGiveaways = giveawayRepository
            .findAllByState(GiveawayState.RUNNING);
        updateMultipleGiveaways(activeGiveaways);
    }

    private void updateMultipleGiveaways(List<GiveawayEntity> activeGiveaways) {
        activeGiveaways.forEach(g -> {
            log.debug("Updating {}", g);
            TextChannel c = jda.getTextChannelById(g.getChannelId());
            if (c != null) {
                c.retrieveMessageById(g.getMessageId()).queue(m -> {
                    m.editMessage(GiveawayEmbedUtils.renderMessage(g)).queue();
                });
            }
        });
    }

    private void updateEndedGiveaways() {
        List<GiveawayEntity> endingGiveaways = giveawayRepository
            .findAllByEndsAtBeforeAndStateIs(
                new Timestamp(Instant.now().plusSeconds(1).plusMillis(500).toEpochMilli()),
                GiveawayState.RUNNING);
        endingGiveaways.forEach(this::endGiveaway);
    }

    private void endGiveaway(GiveawayEntity giveaway) {
        log.info("Ending giveaway {}", giveaway);
        List<String> winners = determineWinners(giveaway);
        TextChannel channel = jda.getTextChannelById(giveaway.getChannelId());

        String winnersAsMention = winners.stream().map(id -> "<@!" + id + ">")
            .collect(Collectors.joining(" "));
        if (channel != null) {
            if (winners.size() == 0) {
                // Could not determine a winner
                channel.sendMessage("\uD83D\uDEA8 Could not determine a winner!").queue();
            } else {
                if (!giveaway.isSecret()) {
                    channel.sendMessage(
                        ":tada: Congratulations " + winnersAsMention + " you won **" + giveaway
                            .getName() + "**").queue();
                } else {
                    channel.sendMessage(":tada: **" + giveaway.getName()
                        + "** has ended. Stay tuned for the winners!").queue();
                }
                channel.retrieveMessageById(giveaway.getMessageId()).queue(msg -> {
                    msg.editMessage(GiveawayEmbedUtils.renderMessage(giveaway))
                        .queue();
                });
            }
        }

        giveaway.setState(GiveawayState.ENDED);
        giveaway.setFinalWinners(String.join(", ", winners));
        giveawayRepository.save(giveaway);
        entityCache.remove(giveaway.getMessageId());
    }


    private boolean isGiveawayEmote(ReactionEmote emote) {
        if (emote.isEmote() != custom) {
            return false;
        }
        if (custom) {
            return emote.getEmote().getId().equals(this.emoteId);
        } else {
            return emote.getEmoji().equals(this.emoji);
        }
    }

    @EventListener
    @Async
    public void onReactionAdd(GuildMessageReactionAddEvent event) {
        if (event.getUser().isBot() || event.getUser().isFake()) {
            return; // Ignore bots and fake users
        }
        if (isGiveawayEmote(event.getReactionEmote())) {
            GiveawayEntity cached = entityCache.get(event.getMessageId());
            if (cached == null) {
                log.debug("Cache miss. Looking up giveaway entity");
                Optional<GiveawayEntity> e = giveawayRepository
                    .findByMessageId(event.getMessageId());
                if (e.isPresent()) {
                    cached = e.get();
                } else {
                    log.debug("Could not find giveaway for {}", event.getMessageId());
                    return;
                }
                entityCache.put(event.getMessageId(), cached);
            }
            enterGiveaway(event.getUser(), cached);
        }
    }

    @EventListener
    @Async
    @Transactional
    public void onMessageDelete(MessageDeleteEvent event) {
        giveawayRepository.deleteAllByMessageId(event.getMessageId());
    }

    private void enterGiveaway(User user, GiveawayEntity entity) {
        if (entrantRepository.existsByGiveawayAndUserId(entity, user.getId())) {
            log.debug("{} has already entered {}", user, entity);
        } else {
            if (entity.getState() != GiveawayState.RUNNING) {
                log.debug("Not entering {} into {}. Has already ended", user, entity);
                return;
            }
            log.debug("Entering {} into {}", user, entity);
            GiveawayEntrantEntity gee = new GiveawayEntrantEntity(entity, user.getId());
            entrantRepository.save(gee);
        }
    }
}
