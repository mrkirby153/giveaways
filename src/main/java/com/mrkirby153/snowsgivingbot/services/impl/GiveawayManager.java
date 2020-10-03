package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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

    private final Map<String, GiveawayEntity> entityCache = new HashMap<>();

    private final AtomicLong counter = new AtomicLong(0);

    private final String emoji;
    private final boolean custom;
    private final String emoteId;

    private final Object endingGiveawayLock = new Object();
    private final Random random = new Random();
    private final List<Long> endingGiveaways = new CopyOnWriteArrayList<>();

    private boolean isReady = false;

    public GiveawayManager(ShardManager shardManager, EntrantRepository entrantRepository,
        GiveawayRepository giveawayRepository,
        DiscordService discordService,
        @Value("${bot.reaction:" + TADA + "}") String emote,
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
        Timestamp endsAt = new Timestamp(System.currentTimeMillis() + Time.parse(endsIn));
        // 2038 problem workaround
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
        deleteGiveaway(entity);
    }

    @Override
    public void deleteGiveaway(GiveawayEntity entity) {
        TextChannel c = shardManager.getTextChannelById(entity.getChannelId());
        if (c != null) {
            c.deleteMessageById(entity.getMessageId()).queue();
        }
        publisher.publishEvent(new GiveawayEndedEvent(entity));
        giveawayRepository.delete(entity);
    }

    @Override
    public List<String> determineWinners(GiveawayEntity giveaway) {
        return determineWinners(giveaway, Collections.emptyList(), giveaway.getWinners());
    }

    @Override
    public List<String> determineWinners(GiveawayEntity giveaway, List<String> existingWinners,
        int amount) {
        List<String> allIds = entrantRepository.findAllIdsFromGiveaway(giveaway);
        List<String> winList = new ArrayList<>();
        while (winList.size() < amount && !allIds.isEmpty()) {
            String potentialWinner;
            do {
                potentialWinner = allIds.remove(random.nextInt(allIds.size()));
            } while (existingWinners.contains(potentialWinner));
            winList.add(potentialWinner);
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

        TextChannel chan = shardManager.getTextChannelById(ge.getChannelId());
        if (chan == null || !chan.canTalk()) {
            throw new IllegalArgumentException(
                "I can't talk in the giveaway channel, so I cannot reroll");
        }

        List<String> existingWinners = Arrays.stream(ge.getFinalWinners()).map(String::trim)
            .collect(Collectors.toList());
        List<String> newWinners;
        List<String> allWinners = new ArrayList<>();

        if (users != null && users.length > 0) {
            log.debug("Rerolling with existing users");
            for (String s : users) {
                existingWinners.remove(s.trim());
            }
            log.debug("picking {} new winners for {}", users.length, ge.getId());
            newWinners = determineWinners(ge, existingWinners, users.length);
            allWinners.addAll(existingWinners);
        } else {
            log.debug("Rerolling with new users");
            newWinners = determineWinners(ge);
        }
        allWinners.addAll(newWinners);

        // This is a total hack. Set the final winners of the giveaway, render the end message so
        // it only shows the new winners, then set the final winners of the giveaway so the embed
        // updates
        ge.setFinalWinners(newWinners.toArray(new String[0]));
        List<String> messages = generateEndMessage(ge, true);
        messages.forEach(m -> chan.sendMessage(m).queue());
        ge.setFinalWinners(allWinners.toArray(new String[0]));
        ge = giveawayRepository.save(ge);
        renderGiveaway(ge);
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
        if (chan == null || !chan.canTalk()) {
            return;
        }
        chan.retrieveMessageById(entity.getMessageId()).queue(msg -> {
            msg.editMessage(GiveawayEmbedUtils.renderMessage(entity)).queue();
        });
    }

    /**
     * Updates all running giveaways that end before the given timestamp
     *
     * @param before The timestamp
     */
    private void updateGiveaways(Timestamp before) {
        List<GiveawayEntity> giveaways = giveawayRepository
            .findAllByEndsAtBeforeAndStateIs(before, GiveawayState.RUNNING);
        updateMultipleGiveaways(giveaways, false);
    }

    @Scheduled(fixedDelay = 1000L) // 1 Second
    public void updateGiveaways() {
        if (!isReady) {
            return;
        }
        endEndedGiveaways();
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

    /**
     * Updates multiple giveaways at the same time
     *
     * @param activeGiveaways The list of giveaways to update
     * @param schedule        If the giveaway update should be spread across the next minute.
     *                        This is used to prevent running into the global ratelimit
     */
    private synchronized void updateMultipleGiveaways(List<GiveawayEntity> activeGiveaways,
        boolean schedule) {
        List<GiveawayEntity> filtered = new ArrayList<>(activeGiveaways);
        filtered.removeIf(entity -> endingGiveaways.contains(entity.getId()));
        if (!schedule) {
            filtered.forEach(this::renderGiveaway);
        } else {
            log.debug("Queueing giveaway updates over the next 60 seconds");
            Map<Long, List<GiveawayEntity>> bucketed = new HashMap<>();
            filtered.forEach(g -> {
                long second = g.getId() % 60;
                bucketed.computeIfAbsent(second, l -> new ArrayList<>()).add(g);
            });
            bucketed.forEach((key, value) -> {
                log.debug(" - {}: {} giveaways", key, value.size());
                taskScheduler.schedule(() -> value.forEach(this::renderGiveaway),
                    Instant.now().plusSeconds(key));
            });
        }
    }

    /**
     * Renders a giveaway's embed
     *
     * @param entity The giveaway to render
     */
    private void renderGiveaway(GiveawayEntity entity) {
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

    /**
     * Ends all running giveaways whose end time is before 1.5s from now
     */
    private void endEndedGiveaways() {
        List<GiveawayEntity> endingGiveaways = giveawayRepository
            .findAllByEndsAtBeforeAndStateIs(
                new Timestamp(Instant.now().plusSeconds(1).plusMillis(500).toEpochMilli()),
                GiveawayState.RUNNING);
        endingGiveaways.forEach(this::endGiveaway);
    }

    /**
     * Ends the provided giveaway
     *
     * @param giveaway The giveaway to end
     */
    private void endGiveaway(GiveawayEntity giveaway) {
        endGiveaway(giveaway, false);
    }

    /**
     * Generates a series of messages used for the giveaway ending
     *
     * @param entity         The entity to generate the giveaways from
     * @param includeMsgLink If a message link should be included
     *
     * @return A list of messages that should be sent to announce the end of the giveaway
     */
    private List<String> generateEndMessage(GiveawayEntity entity, boolean includeMsgLink) {
        String msgLink = String
            .format("<https://discordapp.com/channels/%s/%s/%s>", entity.getGuildId(),
                entity.getChannelId(), entity.getMessageId());
        List<String> winnerMentions = Arrays.stream(entity.getFinalWinners())
            .map(id -> String.format("<@!%s>", id)).collect(
                Collectors.toList());

        if (entity.getFinalWinners().length == 0) {
            return Collections.singletonList(
                "\uD83D\uDEA8 Could not determine a winner! \uD83D\uDEA8" + (includeMsgLink ? "\n"
                    + msgLink : ""));
        }

        String winMessage = String
            .format(":tada: Congratulations %s, you won **%s**", String.join(" ", winnerMentions),
                entity.getName());
        if (includeMsgLink) {
            winMessage = winMessage + "\n" + msgLink;
        }
        if (winMessage.length() < 1900) {
            return Collections.singletonList(winMessage);
        }
        // We need to split messages
        List<String> messages = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        builder.append(":tada: Congratulations ");
        for (String mention : winnerMentions) {
            if (builder.length() + mention.length() + 1 > 1990) {
                messages.add(builder.toString());
                builder = new StringBuilder();
            }
            builder.append(String.format("%s ", mention));
        }
        String msg = String.format("you won **%s**", entity.getName());
        if (includeMsgLink) {
            msg = msg + "\n" + msgLink;
        }
        if (builder.length() + msg.length() >= 1990) {
            messages.add(builder.toString());
            builder = new StringBuilder();
        }
        builder.append(msg);
        if (builder.length() > 0) {
            messages.add(builder.toString());
        }
        return messages;
    }

    private synchronized void endGiveaway(GiveawayEntity giveaway, boolean reroll) {
        if (endingGiveaways.contains(giveaway.getId())) {
            log.debug("Giveaway {} is alrady ending", giveaway);
            return;
        }
        log.info("Ending giveaway {}", giveaway);
        sws.removeFromWorker(giveaway);
        endingGiveaways.add(giveaway.getId());
        taskExecutor.execute(() -> {
            try {
                synchronized (endingGiveawayLock) {
                    giveaway.setState(GiveawayState.ENDING);
                    TextChannel channel = shardManager.getTextChannelById(giveaway.getChannelId());
                    if (channel == null || !channel.getGuild().getSelfMember()
                        .hasPermission(channel, Permission.MESSAGE_READ,
                            Permission.MESSAGE_WRITE)) {
                        log.info(
                            "Can't end giveaway {}. Channel not found or missing MESSAGE_READ/MESSAGE_WRITE",
                            giveaway);
                        return;
                    }
                    renderGiveaway(giveaway);
                    long standaloneQueueSize = 0;
                    while (backfillService.isBackfilling(giveaway)
                        || (standaloneQueueSize = rqs.queueSize(giveaway.getId())) > 0) {
                        log.debug("Giveaway is still being processed. Queue Size: {}",
                            standaloneQueueSize);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }
                    List<String> winners = determineWinners(giveaway);
                    giveaway.setFinalWinners(winners.toArray(new String[0]));
                    if (giveaway.isSecret() && !reroll) {
                        channel.sendMessage(String
                            .format(":tada: **%s** has ended. Stay tuned for the winners",
                                giveaway.getName())).queue();
                        return;
                    }
                    boolean includeLink = true;
                    if (channel.hasLatestMessage() && channel.getLatestMessageId()
                        .equals(giveaway.getMessageId())) {
                        includeLink = false;
                    }
                    generateEndMessage(giveaway, includeLink)
                        .forEach(msg -> channel.sendMessage(msg).queue());
                    giveaway.setState(GiveawayState.ENDED);
                    renderGiveaway(giveaway);
                }
            } catch (Exception e) {
                log.error("Error ending giveaway {}", giveaway, e);
            } finally {
                giveaway.setState(GiveawayState.ENDED);
                GiveawayEntity saved = giveawayRepository.save(giveaway);
                entityCache.remove(giveaway.getMessageId());
                publisher.publishEvent(new GiveawayEndedEvent(saved));
                endingGiveaways.remove(giveaway.getId());
            }
        });
    }

    /**
     * Checks if the given reaction emote is the giveaway reaction emote
     *
     * @param emote The reaction emote
     *
     * @return True if the reaction emote is a giveaway reaction emote
     */
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
        try {
            deleteGiveaway(event.getMessageId());
        } catch (IllegalArgumentException e) {
            // Ignore
        }
    }

    @EventListener
    public void onReady(AllShardsReadyEvent event) {
        log.debug("Bot is ready");
        isReady = true;
    }
}
