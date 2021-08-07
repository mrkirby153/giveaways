package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayEndedEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayEnterEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import com.mrkirby153.snowsgivingbot.services.AdminLoggerService;
import com.mrkirby153.snowsgivingbot.services.DiscordService;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import com.mrkirby153.snowsgivingbot.services.RabbitMQService;
import com.mrkirby153.snowsgivingbot.services.StandaloneWorkerService;
import com.mrkirby153.snowsgivingbot.services.backfill.GiveawayBackfillService;
import com.mrkirby153.snowsgivingbot.services.setting.SettingService;
import com.mrkirby153.snowsgivingbot.services.setting.Settings;
import com.mrkirby153.snowsgivingbot.utils.GiveawayEmbedUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.transaction.Transactional;

@Service
@Slf4j
public class GiveawayManager implements GiveawayService {

    private static final String TADA = "\uD83C\uDF89";

    private static final List<MentionType> END_MESSAGE_ALLOWED_MENTIONS = Arrays.asList(
        MentionType.USER, MentionType.CHANNEL, MentionType.EMOTE);

    private final ShardManager shardManager;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;
    private final DiscordService discordService;
    private final StandaloneWorkerService sws;
    private final RabbitMQService rabbitMQService;
    private final ApplicationEventPublisher publisher;
    private final TaskExecutor taskExecutor;
    private final TaskScheduler taskScheduler;
    private final GiveawayBackfillService backfillService;
    private final SettingService settingService;
    private final AdminLoggerService adminLoggerService;
    private final MeterRegistry meterRegistry;

    private final Map<String, GiveawayEntity> entityCache = new HashMap<>();

    private final String emoji;
    private final boolean custom;
    private final String emoteId;

    private final Object endingGiveawayLock = new Object();
    private final Random random = new Random();
    private final List<Long> endingGiveaways = new CopyOnWriteArrayList<>();


    private final Counter giveawayEntrantsCounter;
    private final Counter giveawaysStartedCounter;
    private final Counter giveawaysEndedCounter;
    private final AtomicLong runningGiveawayGauge;

    private boolean isReady = false;

    public GiveawayManager(ShardManager shardManager, EntrantRepository entrantRepository,
        GiveawayRepository giveawayRepository,
        DiscordService discordService,
        @Value("${bot.reaction:" + TADA + "}") String emote,
        ApplicationEventPublisher aep,
        TaskExecutor taskExecutor, StandaloneWorkerService sws, RabbitMQService rabbitMQService,
        TaskScheduler taskScheduler,
        @Lazy GiveawayBackfillService backfillService,
        SettingService settingService, AdminLoggerService adminLoggerService,
        MeterRegistry meterRegistry) {
        this.shardManager = shardManager;
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
        this.discordService = discordService;
        this.publisher = aep;
        this.taskExecutor = taskExecutor;
        this.sws = sws;
        this.rabbitMQService = rabbitMQService;
        this.taskScheduler = taskScheduler;
        this.backfillService = backfillService;
        this.settingService = settingService;
        this.adminLoggerService = adminLoggerService;
        this.meterRegistry = meterRegistry;

        giveawayEntrantsCounter = meterRegistry.counter("giveaway_entrants");
        giveawaysStartedCounter = meterRegistry.counter("giveaway_started");
        giveawaysEndedCounter = meterRegistry.counter("giveaway_ended");
        runningGiveawayGauge = meterRegistry.gauge("running_giveaways", new AtomicLong(0));

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
        entity.setMessageId("<pending>");
        entity = giveawayRepository.save(entity);

        GiveawayEntity finalEntity = entity;
        channel.sendMessage(GiveawayEmbedUtils.renderMessage(entity, settingService)).queue(m -> {
            finalEntity.setMessageId(m.getId());
            if (!settingService.get(Settings.USE_BUTTONS, channel.getGuild())) {
                addGiveawayEmote(m);
            }
            GiveawayEntity save = giveawayRepository.save(finalEntity);
            publisher.publishEvent(new GiveawayStartedEvent(save));
            giveawaysStartedCounter.increment();
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
        messages.forEach(
            m -> chan.sendMessage(m).allowedMentions(END_MESSAGE_ALLOWED_MENTIONS)
                .mentionUsers(newWinners.toArray(new String[0])).queue());
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
            publisher.publishEvent(new GiveawayEnterEvent(user, entity));
            giveawayEntrantsCounter.increment();
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
            msg.editMessage(GiveawayEmbedUtils.renderMessage(entity, settingService)).queue();
        });
    }

    @Override
    public void renderGiveaway(GiveawayEntity entity) {
        log.debug("Updating {}", entity);
        TextChannel c = shardManager.getTextChannelById(entity.getChannelId());
        if (c != null) {
            // Check if we have permission to update the giveaway
            if (c.getGuild().getSelfMember()
                .hasPermission(c, Permission.MESSAGE_WRITE, Permission.MESSAGE_READ)) {
                c.retrieveMessageById(entity.getMessageId()).queue(m -> {
                    m.editMessage(GiveawayEmbedUtils.renderMessage(entity, settingService)).queue();
                });
            }
        }
    }

    @Scheduled(fixedDelay = 1000L) // 1 Second
    public void updateGiveaways() {
        if (!isReady) {
            return;
        }
        endEndedGiveaways();
    }

    /**
     * Adds the giveaway emote to the given message
     *
     * @param message The message to add the emote to
     */
    private void addGiveawayEmote(Message message) {
        addGiveawayEmote(message, false);
    }

    /**
     * Adds the giveaway emote to the given message.
     *
     * If a custom emote is set up and it fails to apply, it will  be reset and the default emote
     * added.
     *
     * @param message      The message to add the emote to
     * @param forceDefault If the default emote should be forced
     */
    private void addGiveawayEmote(Message message, boolean forceDefault) {
        ConfiguredGiveawayEmote cge = settingService
            .get(Settings.GIVEAWAY_EMOTE, message.getGuild());
        if (cge == null || forceDefault) {
            if (custom) {
                message.addReaction(discordService.findEmoteById(emoteId)).queue();
            } else {
                message.addReaction(emoji).queue();
            }
        } else {
            Consumer<? super Throwable> errorHandler = throwable -> {
                if (throwable instanceof ErrorResponseException
                    && ((ErrorResponseException) throwable).getErrorResponse()
                    == ErrorResponse.UNKNOWN_EMOJI) {
                    log.debug(
                        "Custom giveaway emote on {} did not apply correctly, resetting to default",
                        message.getGuild());
                    addGiveawayEmote(message, true);
                    settingService.reset(Settings.GIVEAWAY_EMOTE, message.getGuild());
                }
            };
            if (cge.isCustom()) {
                message.addReaction(discordService.findEmoteById(cge.getEmote()))
                    .queue(null, errorHandler);
            } else {
                message.addReaction(cge.getEmote()).queue(null, errorHandler);
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
                        || (standaloneQueueSize = rabbitMQService.queueSize(giveaway)) > 0) {
                        // TODO: 10/31/20 After 5 minutes or so we should time out and abort to prevent deadlock
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
                    // Force disable jump links if they're disabled in a guild setting
                    if (!settingService.get(Settings.DISPLAY_JUMP_LINKS, channel.getGuild())) {
                        includeLink = false;
                    }
                    generateEndMessage(giveaway, includeLink)
                        .forEach(msg -> channel.sendMessage(msg)
                            .allowedMentions(END_MESSAGE_ALLOWED_MENTIONS)
                            .mentionUsers(winners.toArray(new String[0])).queue());
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
                giveawaysEndedCounter.increment();
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
    private boolean isGiveawayEmote(Guild guild, ReactionEmote emote) {
        ConfiguredGiveawayEmote configured = settingService.get(Settings.GIVEAWAY_EMOTE, guild);
        if (configured == null) {
            if (emote.isEmote() != custom) {
                return false;
            }
            if (custom) {
                return emote.getEmote().getId().equals(this.emoteId);
            } else {
                return emote.getEmoji().equals(this.emoji);
            }
        } else {
            if (emote.isEmote() != configured.isCustom()) {
                return false;
            }
            if (configured.isCustom()) {
                return emote.getEmote().getId().equals(configured.getEmote());
            } else {
                return emote.getEmoji().equals(configured.getEmote());
            }
        }
    }

    @EventListener
    @Async
    public void onReactionAdd(GuildMessageReactionAddEvent event) {
        if (sws.isStandalone(event.getGuild())) {
            return;
        }
        if (event.getUser().isBot()) {
            return; // Ignore bots
        }
        if (isGiveawayEmote(event.getGuild(), event.getReactionEmote())) {
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
    public void onButtonClick(ButtonClickEvent event) {
        String[] id = event.getComponentId().split(":");
        if (id.length != 2) {
            log.warn("Invalid button component id: " + event.getComponentId());
            return;
        }
        String action = id[0];
        String giveaway = id[1];
        Optional<GiveawayEntity> e = giveawayRepository.findById(Long.parseLong(giveaway));
        if (e.isEmpty()) {
            log.warn("Giveaway with {} not found", giveaway);
            return;
        }
        GiveawayEntity ge = e.get();
        if (action.equals("enter")) {
            event.reply("You have been entered into " + ge.getName()).setEphemeral(true).queue();
            enterGiveaway(event.getUser(), ge);
        }
        if (action.equals("check")) {
            boolean entered = entrantRepository
                .existsByGiveawayAndUserId(ge, event.getUser().getId());
            if (entered) {
                event.reply("You are entered into " + ge.getName()).setEphemeral(true).queue();
            } else {
                event.reply("You are **not** entered into " + ge.getName()).setEphemeral(true)
                    .queue();
            }
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

    @Scheduled(fixedDelay = 1000L)
    public void updateStatGauges() {
        log.trace("Updating gauges");
        runningGiveawayGauge.set(giveawayRepository.countAllByState(GiveawayState.RUNNING));
    }

}
