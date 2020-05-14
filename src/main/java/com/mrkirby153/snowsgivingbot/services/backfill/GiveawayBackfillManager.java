package com.mrkirby153.snowsgivingbot.services.backfill;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.services.GiveawayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiveawayBackfillManager implements GiveawayBackfillService {

    private final ExecutorService threadPool = Executors
        .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("Backfill-%s").build());

    private final List<BackfillTask> runningTasks = new CopyOnWriteArrayList<>();
    private final Set<Long> runningGiveaways = new CopyOnWriteArraySet<>();

    private final AtomicLong id = new AtomicLong(1);

    private final GiveawayService giveawayService;
    private final JDA jda;

    @Override
    public BackfillTask startBackfill(GiveawayEntity giveaway) {
        BackfillTask task = new BackfillTask(id.getAndIncrement(), jda, giveawayService, this,
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
}
