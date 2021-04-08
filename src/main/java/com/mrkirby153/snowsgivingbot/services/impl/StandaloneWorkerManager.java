package com.mrkirby153.snowsgivingbot.services.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.GiveawayStartedEvent;
import com.mrkirby153.snowsgivingbot.services.RedisQueueService;
import com.mrkirby153.snowsgivingbot.services.StandaloneWorkerService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StandaloneWorkerManager implements StandaloneWorkerService {

    private static final String STANDALONE_KEY = "standalone";
    private static final String WORKER_LIST_KEY = "worker_load";
    private static final String GIVEAWAY_TOPIC = "giveaway";
    private static final String GIVEAWAY_WORKER_FORMAT = "giveaway-%s";

    private final RedisTemplate<String, String> redisTemplate;
    private final SetOperations<String, String> setOperations;
    private final ZSetOperations<String, String> zSetOperations;
    private final GiveawayRepository giveawayRepository;
    private final RedisQueueService redisQueueService;

    private final LoadingCache<String, Boolean> standaloneCache;

    public StandaloneWorkerManager(RedisTemplate<String, String> redisTemplate,
        GiveawayRepository giveawayRepository, @Lazy RedisQueueService redisQueueService) {
        this.redisTemplate = redisTemplate;
        this.setOperations = redisTemplate.opsForSet();
        this.zSetOperations = redisTemplate.opsForZSet();
        this.giveawayRepository = giveawayRepository;
        this.redisQueueService = redisQueueService;

        this.standaloneCache = CacheBuilder.newBuilder().maximumSize(1000).build(
            new CacheLoader<String, Boolean>() {
                @Override
                public Boolean load(@NotNull String key) throws Exception {
                    Boolean isMember = setOperations.isMember(STANDALONE_KEY, key);
                    if (isMember == null) {
                        return false;
                    }
                    return isMember;
                }
            });
    }

    @Override
    public void enableStandaloneWorker(Guild guild) {
        log.info("Enabling standalone mode for {} ({})", guild.getName(), guild.getId());
        setOperations.add(STANDALONE_KEY, guild.getId());
        List<GiveawayEntity> giveaways = giveawayRepository
            .findAllByGuildIdAndState(guild.getId(), GiveawayState.RUNNING);
        log.debug("Assigning {} giveaways to workers", giveaways.size());
        giveaways.forEach(giveaway -> {
            sendToWorker(giveaway);
            redisQueueService.assign(giveaway.getId());
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        standaloneCache.invalidate(guild.getId());
    }

    @Override
    public Set<String> getStandaloneGuilds() {
        return setOperations.members(STANDALONE_KEY);
    }

    @Override
    public void disableStandaloneWorker(Guild guild) {
        log.info("Disabling standalone mode for {} ({})", guild.getName(), guild.getId());
        setOperations.remove(STANDALONE_KEY, guild.getId());
        List<GiveawayEntity> giveaways = giveawayRepository
            .findAllByGuildIdAndState(guild.getId(), GiveawayState.RUNNING);
        log.debug("Unassigning {} giveaways from workers", giveaways.size());
        giveaways.forEach(g -> {
            removeFromWorker(g);
            redisQueueService.dequeue(g);
        });
        standaloneCache.invalidate(guild.getId());
    }

    @Override
    public boolean isStandalone(Guild guild) {
        return isStandalone(guild.getId());
    }

    @Override
    public Boolean isStandalone(String id) {
        try {
            return standaloneCache.get(id);
        } catch (ExecutionException e) {
            log.error("Could not determine standalone status for {}", id, e);
        }
        return false;
    }

    @Override
    public void sendToWorker(GiveawayEntity giveaway) {
        String workerId = getWorker();
        log.debug("Sending giveaway {} to worker {}", giveaway.getId(), workerId);
        redisTemplate.convertAndSend(String.format(GIVEAWAY_WORKER_FORMAT, workerId),
            String.format("load:%d-%s", giveaway.getId(), giveaway.getMessageId()));
    }

    @Override
    public void removeFromWorker(GiveawayEntity giveaway) {
        log.debug("Removing giveaway {} from the workers", giveaway.getId());
        redisTemplate.convertAndSend(GIVEAWAY_TOPIC, String.format("unload:%d", giveaway.getId()));
    }

    @Override
    public Map<String, Long> getWorkerHeartbeats() {
        Map<String, Long> heartbeats = new HashMap<>();
        Set<String> keys = redisTemplate.keys("heartbeat:*");
        if (keys != null) {
            keys.forEach(key -> {
                String s = redisTemplate.opsForValue().get(key);
                if (s != null) {
                    heartbeats.put(key, Long.parseLong(s));
                }
            });
        }
        return heartbeats;
    }

    @Override
    public Map<String, Double> getWorkerLoad() {
        Map<String, Double> load = new HashMap<>();
        Set<TypedTuple<String>> values = zSetOperations.rangeWithScores(WORKER_LIST_KEY, 0, -1);
        if (values != null) {
            values.forEach(val -> load.put(val.getValue(), val.getScore()));
        }
        return load;
    }

    @Override
    public long distributeUntrackedGiveaways(Guild guild) {
        List<Long> distributedGiveaways = new ArrayList<>();
        redisTemplate.keys("worker:*:giveaways").forEach(key -> distributedGiveaways
            .addAll(setOperations.members(key).stream().map(Long::parseLong).collect(
                Collectors.toList())));
        log.debug("{} distributed giveaways across workers", distributedGiveaways.size());
        List<Long> storedGiveaways = giveawayRepository
            .findAllByGuildIdAndState(guild.getId(), GiveawayState.RUNNING).stream()
            .map(GiveawayEntity::getId).collect(
                Collectors.toList());
        log.debug("{} running stored giveaways", storedGiveaways.size());
        storedGiveaways.removeAll(distributedGiveaways);
        log.debug("{} giveaways to distribute", storedGiveaways.size());
        giveawayRepository.findAllById(storedGiveaways).forEach(g -> {
            sendToWorker(g);
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        return storedGiveaways.size();
    }

    @EventListener
    public void onGiveawayStart(GiveawayStartedEvent event) {
        if (isStandalone(event.getGiveaway().getGuildId())) {
            sendToWorker(event.getGiveaway());
        }
    }

    /**
     * Gets the worker with the least load to assign the giveaway to
     *
     * @return The worker id
     */
    private String getWorker() {
        Set<String> workers = zSetOperations.range(WORKER_LIST_KEY, 0, 0);
        if (workers == null) {
            throw new IllegalStateException("No worker was found");
        }
        Iterator<String> iter = workers.iterator();
        if (!iter.hasNext()) {
            throw new IllegalStateException("No worker was found");
        }
        return iter.next();
    }
}
