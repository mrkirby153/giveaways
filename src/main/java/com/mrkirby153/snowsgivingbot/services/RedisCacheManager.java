package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import com.mrkirby153.snowsgivingbot.entity.repo.EntrantRepository;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.GiveawayEndedEvent;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisCacheManager implements RedisCacheService, CommandLineRunner {

    private static final AtomicLong cacheWorkerId = new AtomicLong(0);
    private final EntrantRepository entrantRepository;
    private final GiveawayRepository giveawayRepository;
    private final SetOperations<String, String> setOps;
    private final RedisTemplate<String, String> template;
    private final Map<Long, RedisCacheWorker> workers = new ConcurrentHashMap<>();
    protected int sleepDelay = 100;
    private int batchSize = 100;


    public RedisCacheManager(RedisTemplate<String, String> template,
        EntrantRepository entrantRepository, GiveawayRepository giveawayRepository) {
        this.template = template;
        this.setOps = template.opsForSet();
        this.entrantRepository = entrantRepository;
        this.giveawayRepository = giveawayRepository;
    }

    @Override
    public void loadIntoCache(GiveawayEntity giveawayEntity) {
        log.info("Loading {} into the cache", giveawayEntity.getId());
        String key = "giveaway:" + giveawayEntity.getId();
        template.delete(key);
        setOps.add(key,
            entrantRepository.findAllByGiveaway(giveawayEntity).stream().map(
                GiveawayEntrantEntity::getUserId).toArray(String[]::new));
        log.info("Loaded {} into the cache", giveawayEntity.getId());
    }

    @Override
    public void cacheUser(GiveawayEntity giveaway, String user) {
        String key = "giveaway:" + giveaway.getId();
        setOps.add(key, user);
    }

    @Override
    public boolean entered(User user, GiveawayEntity giveaway) {
        Boolean member = setOps.isMember("giveaway:" + giveaway.getId(), user.getId());
        if (member == null) {
            return false;
        }
        return member;
    }

    @Override
    public void queueEntrant(GiveawayEntity giveawayEntity, User user) {
        String key = "queue:" + giveawayEntity.getId();
        log.debug("Enqueueing entrant {} to {}", user, giveawayEntity);
        setOps.add(key, user.getId());
    }

    @Override
    public List<GiveawayEntrantEntity> processQueue(GiveawayEntity giveawayEntity, long amount) {
        List<GiveawayEntrantEntity> entrants = new ArrayList<>();
        List<String> raw = setOps.pop("queue:" + giveawayEntity.getId(), amount);
        if (raw == null) {
            return entrants;
        }
        raw.forEach(s -> {
            GiveawayEntrantEntity gee = new GiveawayEntrantEntity(giveawayEntity, s);
            entrants.add(gee);
        });
        return entrants;
    }

    @Override
    public long queueSize(GiveawayEntity giveawayEntity) {
        Long size = setOps.size("queue:" + giveawayEntity.getId());
        if (size == null) {
            return 0;
        }
        return size;
    }

    @Override
    public long queueSize() {
        long totalSize;
        Set<String> keys = template.keys("queue:*");
        if (keys == null) {
            return 0;
        }
        totalSize = keys.stream().map(setOps::size).filter(Objects::nonNull).mapToLong(size -> size)
            .sum();
        return totalSize;
    }

    @Override
    public Map<String, Long> allQueues() {
        Set<String> keys = template.keys("queue:*");
        if (keys == null) {
            return null;
        }
        Map<String, Long> entires = new HashMap<>();
        keys.forEach(key -> entires.put(key, setOps.size(key)));
        return entires;
    }

    @Override
    public void uncache(GiveawayEntity giveawayEntity) {
        log.info("Uncaching {}", giveawayEntity.getId());
        template.delete("giveaway:" + giveawayEntity.getId());
        template.delete("queue:" + giveawayEntity.getId());
        log.info("Uncached {}", giveawayEntity.getId());
    }

    @Override
    public void updateWorkerSettings(int batchSize, int sleepDelay) {
        this.batchSize = batchSize;
        this.sleepDelay = sleepDelay;
        log.info("Worker settings chaned to batch size: {}, Sleep delay: {}", batchSize,
            sleepDelay);
    }

    @Override
    public void run(String... args) throws Exception {
        giveawayRepository.findAllByState(GiveawayState.RUNNING).forEach(giveaway -> {
            loadIntoCache(giveaway);
            startWorker(giveaway);
        });
    }

    private void startWorker(GiveawayEntity giveaway) {
        if (this.workers.containsKey(giveaway.getId())) {
            return;
        }
        log.info("Starting worker for {}", giveaway.getId());
        workers.put(giveaway.getId(), new RedisCacheWorker(giveaway));
    }

    private void stopWorker(GiveawayEntity giveaway) {
        log.info("Attempting to stop the worker for {}", giveaway);
        RedisCacheWorker rcw = workers.remove(giveaway.getId());
        if (rcw != null) {
            log.info("Stopping worker for {}", giveaway.getId());
            rcw.setRunning(false);
        }
    }

    @EventListener
    public void onGiveawayStart(GiveawayStartedEvent event) {
        startWorker(event.getGiveaway());
    }

    @EventListener
    public void onGiveawayStop(GiveawayEndedEvent event) {
        stopWorker(event.getGiveaway());
        uncache(event.getGiveaway());
    }

    private class RedisCacheWorker implements Runnable {

        private final GiveawayEntity giveawayEntity;
        private final Thread threadHandle;
        @Setter
        private boolean running = true;

        private RedisCacheWorker(GiveawayEntity giveawayEntity) {
            this.giveawayEntity = giveawayEntity;
            this.threadHandle = new Thread(this, "RedisCache-" + cacheWorkerId.getAndIncrement());
            threadHandle.setDaemon(true);
            this.threadHandle.start();
        }


        @Override
        public void run() {
            log.debug("Starting up worker thread");
            while (running) {
                try {
                    if (batchSize == 0) {
                        Thread.sleep(1000);
                        continue;
                    }
                    List<GiveawayEntrantEntity> entrants = processQueue(giveawayEntity, batchSize);
                    if (entrants.size() == 0) {
                        log.debug("Skipping entrants for {}", giveawayEntity.getId());
                        Thread.sleep(1000);
                        continue;
                    }
                    log.debug("Filtering {} entrants", entrants.size());
                    List<String> previouslyEntered = entrantRepository
                        .findAllPreviouslyEntered(giveawayEntity,
                            entrants.stream().map(GiveawayEntrantEntity::getUserId).collect(
                                Collectors.toList()));
                    entrants.removeIf(e -> previouslyEntered.contains(e.getUserId()));
                    log.debug("Filtered down to {} entrants", entrants.size());
                    if (entrants.size() > 0) {
                        setOps.add("giveaway:" + giveawayEntity.getId(),
                            entrants.stream().map(GiveawayEntrantEntity::getUserId)
                                .toArray(String[]::new));
                    }
                    entrantRepository.saveAll(entrants);
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) {
                        log.error("Worker thread encountered exception", e);
                    }
                }
                try {
                    Thread.sleep(sleepDelay);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            log.debug("Worker thread shut down");
        }
    }
}
