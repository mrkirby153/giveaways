package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.GiveawayEndedEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import com.mrkirby153.snowsgivingbot.services.RedisQueueService;
import com.mrkirby153.snowsgivingbot.services.StandaloneWorkerService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedisQueueManager implements RedisQueueService, CommandLineRunner {

    private static final String GIVEAWAY_QUEUE_FORMAT = "queue:%s";
    private static final String GIVEAWAY_SET_FORMAT = "giveaway:%s";

    private final SetOperations<String, String> setOps;
    private final RedisTemplate<String, String> template;
    private final StandaloneWorkerService standaloneWorkerService;
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;

    private final List<QueueProcessorTask> processors = new ArrayList<>();
    private final Map<Long, QueueProcessorTask> assignedGiveaways = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private long delay = 100;
    @Getter
    @Setter
    private int batchSize = 100;

    private long taskCount = 5;

    public RedisQueueManager(RedisTemplate<String, String> template,
        StandaloneWorkerService standaloneWorkerService, EntrantRepository entrantRepository,
        GiveawayRepository giveawayRepository) {
        this.template = template;
        this.setOps = template.opsForSet();
        this.standaloneWorkerService = standaloneWorkerService;
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
    }

    @Override
    public List<String> processQueue(long giveaway, int amount) {
        List<String> users = setOps.pop(String.format(GIVEAWAY_QUEUE_FORMAT, giveaway), amount);
        if (users == null) {
            return new ArrayList<>();
        }
        return users;
    }

    @Override
    public long queueSize(long giveaway) {
        Long size = setOps.size(String.format(GIVEAWAY_QUEUE_FORMAT, giveaway));
        if (size == null) {
            return 0;
        }
        return size;
    }

    @Override
    public Map<String, Long> allQueues() {
        Set<String> keys = template.keys(String.format(GIVEAWAY_QUEUE_FORMAT, "*"));
        if (keys == null) {
            return new HashMap<>();
        }
        Map<String, Long> entries = new HashMap<>();
        keys.forEach(key -> entries.put(key, setOps.size(key)));
        return entries;
    }

    @Override
    public void updateWorkers(int workerDelay, int batchSize) {
        log.info("Updating worker delay to {} and batch size to {}", workerDelay, batchSize);
        this.delay = workerDelay;
        this.batchSize = batchSize;
    }

    @Override
    public void updateWorkerCount(long count) {
        this.taskCount = count;
        updateTasks();
    }

    @Override
    public long getWorkerCount() {
        return taskCount;
    }

    @Override
    public long getWorkerDelay() {
        return delay;
    }

    @Override
    public void dequeue(GiveawayEntity giveawayEntity) {
        QueueProcessorTask task = assignedGiveaways.get(giveawayEntity.getId());
        if (task != null) {
            log.debug("Dequeueing {}", giveawayEntity.getId());
            task.pendingFinish.add(giveawayEntity.getId());
        }
    }

    /**
     * Assigns a giveaway to a queue processor
     *
     * @param giveaway The giveaway to assign
     */
    public void assign(long giveaway) {
        QueueProcessorTask target = processors.get(0);
        for (QueueProcessorTask potential : processors) {
            if (potential.getAssignedGiveawayCount() < target.getAssignedGiveawayCount()) {
                target = potential;
            }
        }
        log.debug("Assigning giveaway {} to processor {}", giveaway, target.getId());
        target.assignGiveaway(giveaway);
        assignedGiveaways.put(giveaway, target);
    }

    /**
     * Unassigns a giveaway from the processor
     *
     * @param giveaway The giveaway to unassign
     */
    public void unassign(long giveaway) {
        QueueProcessorTask task = assignedGiveaways.get(giveaway);
        if (task != null) {
            log.debug("Unassigning {} from {}", giveaway, task.getId());
            task.unassignGiveaway(giveaway);
        }
    }

    private void updateTasks() {
        if (processors.size() > taskCount) {
            // We need to reduce the task count and reassign processing
            long toRemove = processors.size() - taskCount;
            log.info("Shutting down {} task runners", toRemove);
            List<Long> toReAssign = new ArrayList<>();
            for (int i = 0; i < toRemove; i++) {
                QueueProcessorTask task = processors.get(i);
                task.setRunning(false);
                toReAssign.addAll(task.getAssignedGiveaways());
                processors.remove(task);
            }
            log.debug("Reassigning {} giveaways", toReAssign.size());
            toReAssign.forEach(this::assign);
        } else {
            // We need to start new tasks
            long toStart = taskCount - processors.size();
            log.info("Starting {} task runners", toStart);
            for (int i = 0; i < toStart; i++) {
                QueueProcessorTask qpt = new QueueProcessorTask(this, setOps, entrantRepository,
                    giveawayRepository);
                processors.add(qpt);
            }
        }
    }

    @EventListener
    public void onGiveawayStart(GiveawayStartedEvent event) {
        if (standaloneWorkerService.isStandalone(event.getGiveaway().getGuildId())) {
            assign(event.getGiveaway().getId());
        }
    }

    @EventListener
    public void onGiveawayEnd(GiveawayEndedEvent event) {
        unassign(event.getGiveaway().getId());
        template.delete(String.format(GIVEAWAY_QUEUE_FORMAT, event.getGiveaway().getId()));
        template.delete(String.format(GIVEAWAY_SET_FORMAT, event.getGiveaway().getId()));
    }

    @Override
    public void run(String... args) throws Exception {
        updateTasks();
    }


    @Slf4j
    private static class QueueProcessorTask implements Runnable {

        private static final AtomicLong taskId = new AtomicLong(0);
        private static final String ENTERED_SET_FORMAT = "giveaway:%s";

        @Getter
        private final List<Long> assignedGiveaways = new CopyOnWriteArrayList<>();
        private final List<Long> pendingFinish = new CopyOnWriteArrayList<>();
        private final Map<Long, GiveawayEntity> cachedGiveaways = new ConcurrentHashMap<>();
        private final Thread thread;
        @Getter
        private final long id;
        private final Object syncObject = new Object();
        private final RedisQueueManager redisQueueManager;
        private final EntrantRepository entrantRepository;
        private final GiveawayRepository giveawayRepository;
        private final SetOperations<String, String> setOps;
        @Setter
        private boolean running = true;

        private QueueProcessorTask(RedisQueueManager redisQueueManager,
            SetOperations<String, String> setOps, EntrantRepository entrantRepository,
            GiveawayRepository giveawayRepository) {
            this.id = taskId.getAndIncrement();
            this.thread = new Thread(this, "QueueProcessor-" + id);
            this.redisQueueManager = redisQueueManager;
            this.setOps = setOps;
            this.entrantRepository = entrantRepository;
            this.giveawayRepository = giveawayRepository;

            thread.start();
        }


        public int getAssignedGiveawayCount() {
            return assignedGiveaways.size();
        }

        public void assignGiveaway(long giveaway) {
            boolean shouldNotify = assignedGiveaways.size() == 0;
            assignedGiveaways.add(giveaway);
            if (shouldNotify) {
                synchronized (syncObject) {
                    log.debug("Thread {} assigned work. Notifying", id);
                    syncObject.notify();
                }
            }
        }

        public void unassignGiveaway(long giveawayId) {
            assignedGiveaways.remove(giveawayId);
            cachedGiveaways.remove(giveawayId);
        }


        @Override
        public void run() {
            log.debug("Worker thread {} started", id);
            while (running) {
                try {
                    if (assignedGiveaways.size() == 0) {
                        log.debug("Thread {} has no giveaways assigned. Waiting for new work", id);
                        synchronized (syncObject) {
                            syncObject.wait();
                        }
                        log.debug("Thread {} has been assigned work. Resuming", id);
                    }
                    List<Long> toRemove = new ArrayList<>();
                    for (Long giveawayId : assignedGiveaways) {
                        log.debug("Processing giveaway {}", giveawayId);

                        List<String> entrants = redisQueueManager
                            .processQueue(giveawayId, redisQueueManager.getBatchSize());
                        if (entrants.size() == 0) {
                            log.debug("Skipping entrants on {}", giveawayId);
                            if (pendingFinish.remove(giveawayId)) {
                                log.debug(
                                    "Giveaway has been moved to main and queue is now empty, removing");
                                toRemove.add(giveawayId);
                                continue;
                            }
                            Thread.sleep(5000);
                            continue;
                        }

                        log.debug("[{}] Found {}", giveawayId, entrants.size());
                        List<String> previouslyEntered = entrantRepository
                            .findAllPreviouslyEntered(giveawayId, entrants);
                        entrants.removeIf(previouslyEntered::contains);

                        log.debug("[{}] Filtered down to {}", giveawayId, entrants.size());
                        if (entrants.size() > 0) {
                            setOps.add(String.format(ENTERED_SET_FORMAT, giveawayId),
                                entrants.toArray(String[]::new));
                            GiveawayEntity giveaway = cachedGiveaways.computeIfAbsent(giveawayId,
                                key -> giveawayRepository.findById(giveawayId).orElse(null));
                            if (giveaway == null) {
                                toRemove.add(giveawayId);
                                continue;
                            }
                            entrantRepository.saveAll(
                                entrants.stream().map(id -> new GiveawayEntrantEntity(giveaway, id))
                                    .collect(Collectors.toList()));
                        }
                    }

                    if (toRemove.size() > 0) {
                        toRemove.forEach(id -> {
                            redisQueueManager.unassign(id);
                        });
                    }
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        log.error("Worker thread {} encountered an exception", id, e);
                    }
                }
                try {
                    Thread.sleep(redisQueueManager.getDelay());
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            log.debug("Worker thread {} ended", id);
        }
    }
}
