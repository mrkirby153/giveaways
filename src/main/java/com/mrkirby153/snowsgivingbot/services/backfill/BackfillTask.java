package com.mrkirby153.snowsgivingbot.services.backfill;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.mrkirby153.kcutils.Time;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
@Slf4j
public class BackfillTask {

    private static final int LIMIT = 100;
    private static final Random random = new Random();

    @Getter
    private final long id;
    private final ShardManager shardManager;
    private final GiveawayService giveawayService;
    private final GiveawayBackfillService backfillService;
    private final GiveawayEntity giveaway;
    private final String asciiEmote;
    private final Emote emote;
    private final AtomicLong entered = new AtomicLong(0);
    private boolean initialized = false;
    @Getter
    private boolean errored = false;
    @Getter
    @Setter
    private boolean canceled = false;
    private Message message;
    private Guild guild;

    @Getter
    private long timeTaken;

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private CompletableFuture<Long> future;

    private void initialize() {
        if (initialized) {
            return;
        }
        guild = shardManager.getGuildById(giveaway.getGuildId());
        if (guild == null) {
            log.error("Error backfilling giveaway {}: Guild not found", giveaway.getId());
            errored = true;
            return;
        }
        TextChannel channel = guild.getTextChannelById(giveaway.getChannelId());
        if (channel == null) {
            log.error("Error backfilling giveaway {}: Channel not found", giveaway.getId());
            errored = true;
            return;
        }
        message = channel.retrieveMessageById(giveaway.getMessageId()).complete();
        if (message == null) {
            log.error("Error backfilling giveaway {}: Message not found", giveaway.getId());
            errored = true;
        }
    }

    public void process() {
        long startTime = System.currentTimeMillis();
        try {
            log.debug("Starting backfill of {}", giveaway.getId());
            initialize();
            if(errored) {
                timeTaken = System.currentTimeMillis() - startTime;
                future.completeExceptionally(new BackfillInitializationException("Errored during initialization. Guild, Message, or Channel not found"));
                return;
            }
            ReactionPaginationAction action;
            if (this.asciiEmote != null) {
                action = message.retrieveReactionUsers(this.asciiEmote);
            } else if (this.emote != null) {
                action = message.retrieveReactionUsers(this.emote);
            } else {
                log.error("Error backfilling giveaway {}: Emote not found", giveaway.getId());
                return;
            }
            action = action.setCheck(() -> !canceled).limit(LIMIT);
            action.stream().forEach(user -> {
                if (user.getId().equals(user.getJDA().getSelfUser().getId())) {
                    return;
                }
                long cnt = entered.incrementAndGet();
                log.debug("Backfilling {} ({})", user, cnt);
                giveawayService.enterGiveaway(user, giveaway);
                if (getProcessed() % 100 == 0) {
                    long toSleep = random.nextInt(100 - 50 + 1) + 50;
                    log.debug("Sleeping for {}ms to ease load", toSleep);
                    try {
                        TimeUnit.MILLISECONDS.sleep(toSleep);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            timeTaken = System.currentTimeMillis() - startTime;
            log.debug("Backfill of {} complete! Processed {} users. Took {}", giveaway.getId(),
                getProcessed(),
                Time.INSTANCE.format(1, timeTaken));
        } catch (Exception e) {
            timeTaken = System.currentTimeMillis() - startTime;
            log.error("Backfill of {} failed! Processed {} users in {}", giveaway.getId(),
                getProcessed(), Time.INSTANCE.format(1, timeTaken), e);
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }

    public long getProcessed() {
        return this.entered.get();
    }

    public void cancel() {
        if (canceled) {
            return;
        }
        canceled = true;
        if (future != null) {
            future.completeExceptionally(new InterruptedException("Canceled"));
        }
        backfillService.unregisterTask(this);
    }

    public long getGiveawayId() {
        return giveaway.getId();
    }
}
