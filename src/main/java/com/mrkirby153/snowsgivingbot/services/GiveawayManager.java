package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.GiveawayEndedEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import com.mrkirby153.snowsgivingbot.utils.GiveawayEmbedUtils;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final RedisCacheService redisCacheService;
    private final ApplicationEventPublisher publisher;
    private final TaskExecutor taskExecutor;

    private final Object giveawayLock = new Object();

    private final Map<String, GiveawayEntity> entityCache = new HashMap<>();

    private final AtomicLong counter = new AtomicLong(0);

    private final String emoji;
    private final boolean custom;
    private final String emoteId;

    private final List<Long> endingGiveaways = new CopyOnWriteArrayList<>();

    public GiveawayManager(JDA jda, EntrantRepository entrantRepository,
        GiveawayRepository giveawayRepository,
        DiscordService discordService,
        @Value("${bot.reaction:\uD83C\uDF89}") String emote,
        RedisCacheService redisCacheService,
        ApplicationEventPublisher aep,
        TaskExecutor taskExecutor) {
        this.jda = jda;
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
        this.discordService = discordService;
        this.redisCacheService = redisCacheService;
        this.publisher = aep;
        this.taskExecutor = taskExecutor;

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
        entity.setGuildId(channel.getGuild().getId());
        channel.sendMessage(GiveawayEmbedUtils.renderMessage(entity)).queue(m -> {
            entity.setMessageId(m.getId());
            if (custom) {
                m.addReaction(discordService.findEmoteById(emoteId)).queue();
            } else {
                m.addReaction(emoji).queue();
            }
            GiveawayEntity save = giveawayRepository.save(entity);
            publisher.publishEvent(new GiveawayStartedEvent(save));
            cf.complete(save);
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
        publisher.publishEvent(new GiveawayEndedEvent(entity));
        giveawayRepository.delete(entity);
    }

    @Override
    public List<String> determineWinners(GiveawayEntity giveaway) {
        List<String> winList = new ArrayList<>();
        // Add the already existing winners to the giveaway
        if (giveaway.getFinalWinners() != null) {
            Arrays.stream(giveaway.getFinalWinners().split(",")).map(String::trim)
                .forEach(winList::add);
        }
        List<String> allIds = entrantRepository.findAllIdsFromGiveaway(giveaway);
        while (winList.size() < giveaway.getWinners() && !allIds.isEmpty()) {
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
    public void reroll(String mid, String[] users) {
        GiveawayEntity ge = giveawayRepository.findByMessageId(mid)
            .orElseThrow(() -> new IllegalArgumentException("Giveaway not found"));
        if (ge.getState() != GiveawayState.ENDED) {
            throw new IllegalArgumentException("Cannot reroll an in progress giveaway");
        }
        List<String> toRemove = Arrays.stream(ge.getFinalWinners().split(",")).map(String::trim)
            .collect(
                Collectors.toList());
        if (users != null) {
            log.debug("Rerolling with existing users");
            for (String s : users) {
                toRemove.remove(s.trim());
            }
            ge.setFinalWinners(String.join(",", toRemove));
        } else {
            log.debug("Rerolling with new users");
            ge.setFinalWinners(null);
        }
        endGiveaway(ge, true);
    }

    @Override
    public List<GiveawayEntity> getAllGiveaways(Guild guild) {
        return giveawayRepository.findAllByGuildId(guild.getId());
    }

    @Override
    public void enterGiveaway(User user, GiveawayEntity entity) {
        if (!redisCacheService.entered(user, entity)) {
            redisCacheService.queueEntrant(entity, user);
        }
    }

    @Override
    public Emote getGiveawayEmote() {
        if (!custom) {
            return null;
        }
        return jda.getEmoteById(this.emoteId);
    }

    @Override
    public String getGiveawayEmoji() {
        if (custom) {
            return null;
        }
        return emoji;
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
        endGiveaway(giveaway, false);
    }

    private void endGiveaway(GiveawayEntity giveaway, boolean reroll) {
        log.info("Ending giveaway {}", giveaway);
        if (endingGiveaways.contains(giveaway.getId())) {
            log.info("Giveaway {} is already ending!", giveaway);
            return;
        }
        endingGiveaways.add(giveaway.getId());

        taskExecutor.execute(() -> {
            giveaway.setState(GiveawayState.ENDING);
            giveawayRepository.save(giveaway);
            TextChannel channel = jda.getTextChannelById(giveaway.getChannelId());
            if (channel != null) {
                channel.retrieveMessageById(giveaway.getMessageId())
                    .queue(
                        msg -> msg.editMessage(GiveawayEmbedUtils.renderMessage(giveaway)).queue());
            }

            long queueSize = 0;
            while ((queueSize = redisCacheService.queueSize(giveaway)) > 0) {
                try {
                    log.debug("Giveaway {} has a queue size of {}", giveaway.getId(), queueSize);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            List<String> winners = determineWinners(giveaway);

            String winnersAsMention = winners.stream().map(id -> "<@!" + id + ">")
                .collect(Collectors.joining(" "));
            try {
                if (channel != null) {
                    if (winners.size() == 0) {
                        // Could not determine a winner
                        channel.sendMessage("\uD83D\uDEA8 Could not determine a winner!").queue();
                    } else {
                        if (!giveaway.isSecret()) {
                            String winMessage = String
                                .format(":tada: Congratulations %s you won **%s**",
                                    winnersAsMention,
                                    giveaway.getName());
                            if (winMessage.length() >= 2000) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(":tada: Congratulations ");
                                for (String winner : winners) {
                                    String asMention = String.format("<@!%s> ", winner);
                                    if (sb.length() + asMention.length() > 1990) {
                                        channel.sendMessage(sb.toString()).queue();
                                        sb = new StringBuilder();
                                    }
                                    sb.append(asMention);
                                }
                                String appendMsg = String
                                    .format("you won **%s**", giveaway.getName());
                                if (sb.length() + appendMsg.length() > 1990) {
                                    channel.sendMessage(sb.toString()).queue();
                                    channel.sendMessage(appendMsg).queue();
                                } else {
                                    sb.append(appendMsg);
                                    channel.sendMessage(sb.toString()).queue();
                                }
                            } else {
                                channel.sendMessage(winMessage).queue();
                            }
                        } else {
                            if (!reroll) {
                                channel.sendMessage(":tada: **" + giveaway.getName()
                                    + "** has ended. Stay tuned for the winners!").queue();
                            }
                        }
                        channel.retrieveMessageById(giveaway.getMessageId()).queue(msg -> {
                            msg.editMessage(GiveawayEmbedUtils.renderMessage(giveaway))
                                .queue();
                        });
                    }
                }
            } catch (Exception e) {
                log.error("An error occurred announcing the end of {}", giveaway.getName(), e);
            } finally {
                giveaway.setState(GiveawayState.ENDED);
                giveaway.setFinalWinners(String.join(", ", winners));
                giveawayRepository.save(giveaway);
                entityCache.remove(giveaway.getMessageId());
                publisher.publishEvent(new GiveawayEndedEvent(giveaway));
            }
        });
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
}
