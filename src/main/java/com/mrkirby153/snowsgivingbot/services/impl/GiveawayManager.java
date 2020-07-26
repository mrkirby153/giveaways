package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayEndedEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import com.mrkirby153.snowsgivingbot.services.DiscordService;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import com.mrkirby153.snowsgivingbot.services.RedisQueueService;
import com.mrkirby153.snowsgivingbot.services.StandaloneWorkerService;
import com.mrkirby153.snowsgivingbot.services.backfill.GiveawayBackfillService;
import com.mrkirby153.snowsgivingbot.utils.GiveawayEmbedUtils;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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

    private final ShardManager shardManager;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;
    private final DiscordService discordService;
    private final StandaloneWorkerService sws;
    private final RedisQueueService rqs;
    private final ApplicationEventPublisher publisher;
    private final TaskExecutor taskExecutor;
    private final TaskScheduler taskScheduler;
    private final GiveawayBackfillService backfillService;

    private final Object giveawayLock = new Object();

    private final Map<String, GiveawayEntity> entityCache = new HashMap<>();

    private final AtomicLong counter = new AtomicLong(0);

    private final String emoji;
    private final boolean custom;
    private final String emoteId;

    private final List<Long> endingGiveaways = new CopyOnWriteArrayList<>();

    private boolean isReady = false;

    public GiveawayManager(ShardManager shardManager, EntrantRepository entrantRepository,
        GiveawayRepository giveawayRepository,
        DiscordService discordService,
        @Value("${bot.reaction:\uD83C\uDF89}") String emote,
        ApplicationEventPublisher aep,
        TaskExecutor taskExecutor, StandaloneWorkerService sws, RedisQueueService rqs,
        TaskScheduler taskScheduler,
        @Lazy GiveawayBackfillService backfillService) {
        this.shardManager = shardManager;
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
        this.discordService = discordService;
        this.publisher = aep;
        this.taskExecutor = taskExecutor;
        this.sws = sws;
        this.rqs = rqs;
        this.taskScheduler = taskScheduler;
        this.backfillService = backfillService;

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
        int winners, String endsIn, boolean secret, User host) {
        CompletableFuture<GiveawayEntity> cf = new CompletableFuture<>();
        GiveawayEntity entity = new GiveawayEntity();
        entity.setName(name);
        entity.setWinners(winners);
        Timestamp endsAt = new Timestamp(System.currentTimeMillis() + Time.INSTANCE.parse(endsIn));
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(endsAt.getTime());
        if (cal.get(Calendar.YEAR) >= 2038) {
            throw new IllegalArgumentException(
                "Can't start a giveaway that ends that far in the future!");
        }
        entity.setEndsAt(endsAt);
        entity.setChannelId(channel.getId());
        entity.setSecret(secret);
        entity.setGuildId(channel.getGuild().getId());
        entity.setHost(host.getId());
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
        TextChannel c = shardManager.getTextChannelById(entity.getChannelId());
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

    @Override
    public Emote getGiveawayEmote() {
        if (!custom) {
            return null;
        }
        return shardManager.getEmoteById(this.emoteId);
    }

    @Override
    public String getGiveawayEmoji() {
        if (custom) {
            return null;
        }
        return emoji;
    }

    @Override
    public void update(GiveawayEntity entity) {
        Guild g = shardManager.getGuildById(entity.getGuildId());
        if (g == null) {
            return;
        }
        TextChannel chan = g.getTextChannelById(entity.getChannelId());
        if (chan == null) {
            return;
        }
        chan.retrieveMessageById(entity.getMessageId()).queue(msg -> {
            msg.editMessage(GiveawayEmbedUtils.renderMessage(entity)).queue();
        });
    }

    private void updateGiveaways(Timestamp before) {
        synchronized (giveawayLock) {
            List<GiveawayEntity> giveaways = giveawayRepository
                .findAllByEndsAtBeforeAndStateIs(before, GiveawayState.RUNNING);
            updateMultipleGiveaways(giveaways, false);
        }
    }

    @Scheduled(fixedDelay = 1000L) // 1 Second
    public void updateGiveaways() {
        if (!isReady) {
            return;
        }
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
        if (!isReady) {
            return;
        }
        log.debug("Updating all giveaways");
        List<GiveawayEntity> activeGiveaways = giveawayRepository
            .findAllByState(GiveawayState.RUNNING);
        updateMultipleGiveaways(activeGiveaways, true);
    }

    private void updateMultipleGiveaways(List<GiveawayEntity> activeGiveaways, boolean schedule) {
        if (!schedule) {
            activeGiveaways.forEach(this::doUpdate);
        } else {
            log.debug("Queueing giveaway updates over the next 60 seconds");
            Map<Long, List<GiveawayEntity>> bucketed = new HashMap<>();
            activeGiveaways.forEach(g -> {
                long second = g.getId() % 60;
                bucketed.computeIfAbsent(second, l -> new ArrayList<>()).add(g);
            });
            bucketed.forEach((key, value) -> {
                log.debug(" - {}: {} giveaways", key, value.size());
                taskScheduler.schedule(() -> value.forEach(this::doUpdate),
                    Instant.now().plusSeconds(key));
            });
        }
    }

    private void doUpdate(GiveawayEntity entity) {
        log.debug("Updating {}", entity);
        TextChannel c = shardManager.getTextChannelById(entity.getChannelId());
        if (c != null) {
            // Check if we have permission to update the giveaway
            if (c.getGuild().getSelfMember()
                .hasPermission(c, Permission.MESSAGE_WRITE, Permission.MESSAGE_READ)) {
                c.retrieveMessageById(entity.getMessageId()).queue(m -> {
                    m.editMessage(GiveawayEmbedUtils.renderMessage(entity)).queue();
                });
            }
        }
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
        // Prevent giveaways from ending twice
        if (endingGiveaways.contains(giveaway.getId())) {
            log.info("Giveaway {} is already ending!", giveaway);
            return;
        }
        endingGiveaways.add(giveaway.getId());
        sws.removeFromWorker(giveaway);

        taskExecutor.execute(() -> {
            giveaway.setState(GiveawayState.ENDING);
            giveawayRepository.save(giveaway);
            TextChannel channel = shardManager.getTextChannelById(giveaway.getChannelId());
            if (channel != null && channel.getGuild().getSelfMember()
                .hasPermission(channel, Permission.MESSAGE_READ, Permission.MESSAGE_WRITE)) {
                channel.retrieveMessageById(giveaway.getMessageId())
                    .queue(
                        msg -> msg.editMessage(GiveawayEmbedUtils.renderMessage(giveaway))
                            .queue());
            }

            long queueSize = 0;
            while (backfillService.isBackfilling(giveaway)) {
                try {
                    log.debug("Giveaway {} is being backfilled.", giveaway.getId());
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            while ((queueSize = rqs.queueSize(giveaway.getId())) > 0) {
                try {
                    log.debug("Giveaway {} has a queue size of {}", giveaway.getId(), queueSize);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            List<String> winners = determineWinners(giveaway);

            String winnersAsMention = winners.stream().map(id -> "<@!" + id + ">")
                .collect(Collectors.joining(" "));
            try {
                if (channel != null && channel.getGuild().getSelfMember()
                    .hasPermission(channel, Permission.MESSAGE_READ, Permission.MESSAGE_WRITE)) {
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
                taskScheduler.schedule(() -> {
                    log.debug("Removing {} from finished giveaways", giveaway.getId());
                    endingGiveaways.remove(giveaway.getId());
                }, Instant.now().plusSeconds(30));
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
        if (sws.isStandalone(event.getGuild())) {
            return;
        }
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

    @EventListener
    public void onReady(AllShardsReadyEvent event) {
        log.debug("Bot is ready");
        isReady = true;
    }
}
