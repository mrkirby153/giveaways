package com.mrkirby153.snowsgivingbot.services.backfill;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import com.mrkirby153.snowsgivingbot.services.AdminLoggerService;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiveawayBackfillManager implements GiveawayBackfillService {

    private final ExecutorService threadPool = Executors
        .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("Backfill-%s").build());

    private final List<BackfillTask> runningTasks = new CopyOnWriteArrayList<>();
    private final Set<Long> runningGiveaways = new CopyOnWriteArraySet<>();

    private final Set<Long> pendingBackfills = new CopyOnWriteArraySet<>();

    private final AtomicLong id = new AtomicLong(1);

    private final GiveawayService giveawayService;
    private final GiveawayRepository giveawayRepository;
    private final ShardManager shardManager;
    private final AdminLoggerService adminLogger;

    @Override
    public BackfillTask startBackfill(GiveawayEntity giveaway) {
        BackfillTask task = new BackfillTask(id.getAndIncrement(), shardManager, giveawayService,
            this,
            giveaway,
            giveawayService.getGiveawayEmoji(), giveawayService.getGiveawayEmote());
        CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
            task.process();
            return task.getProcessed();
        }, threadPool);
        future = future.handle((result, throwable) -> {
            runningGiveaways.remove(giveaway.getId());
            runningTasks.remove(task);
            return result;
        });
        task.setFuture(future);
        runningTasks.add(task);
        runningGiveaways.add(giveaway.getId());
        return task;
    }

    @Override
    public List<BackfillTask> getRunning() {
        return runningTasks;
    }

    @Override
    public Set<Long> getRunningGiveawayIDs() {
        return runningGiveaways;
    }

    @Override
    public void unregisterTask(BackfillTask task) {
        log.debug("Unregistering task {}", task.getId());
        runningTasks.remove(task);
        runningGiveaways.remove(task.getGiveawayId());
    }

    @Override
    public boolean isBackfilling(GiveawayEntity giveawayEntity) {
        return getRunningGiveawayIDs().contains(giveawayEntity.getId());
    }

    private void runNextQueuedTask() {
        log.info("{} pending backfills remaining", pendingBackfills.size());
        if(pendingBackfills.size() == 0) {
            adminLogger.log("All giveaways have been backfilled");
        }
        log.debug("Running next backfill task");
        Iterator<Long> iter = pendingBackfills.iterator();
        if (iter.hasNext()) {
            Long next = iter.next();
            log.debug("Next giveaway is {}", next);
            Optional<GiveawayEntity> giveaway = giveawayRepository.findById(next);
            if (giveaway.isPresent()) {
                BackfillTask task = startBackfill(giveaway.get());
                task.getFuture().handle((c, t) -> {
                    if (t != null) {
                        log.info("Giveaway {} did not backfill correctly", giveaway.get().getId());
                        if (t instanceof BackfillInitializationException) {
                            log.info("Deleting giveaway because it failed initialization: {}",
                                t.getMessage());
                            giveawayService.deleteGiveaway(giveaway.get());
                        } else {
                            adminLogger.log(String
                                .format("Giveaway %s did not backfill correctly: %s",
                                    giveaway.get().getId(), t.getMessage()));
                        }
                    }
                    log.debug("Backfill completed");
                    runNextQueuedTask();
                    return c;
                });
            }
            pendingBackfills.remove(next);
        }
    }

    @EventListener
    public void onReady(AllShardsReadyEvent event) {
        // Queue all running giveaways for backfill
        List<GiveawayEntity> runningGiveaways = giveawayRepository
            .findAllByState(GiveawayState.RUNNING);
        log.debug("There are {} running giveaways to backfill", runningGiveaways.size());
        pendingBackfills.addAll(
            runningGiveaways.stream().map(GiveawayEntity::getId).collect(Collectors.toList()));
        if (runningGiveaways.size() > 0) {
            adminLogger.log(String.format("Backfilling %d giveaways", runningGiveaways.size()));
        }
        runNextQueuedTask();
    }
}
