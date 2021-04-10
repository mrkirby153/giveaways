package com.mrkirby153.snowsgivingbot.services.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.event.AllShardsReadyEvent;
import com.mrkirby153.snowsgivingbot.services.RabbitMQService;
import com.mrkirby153.snowsgivingbot.services.StandaloneWorkerService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StandaloneWorkerManager implements StandaloneWorkerService {

    private static final String STANDALONE_KEY = "standalone";

    private final RedisTemplate<String, String> redisTemplate;
    private final SetOperations<String, String> setOperations;
    private final ZSetOperations<String, String> zSetOperations;
    private final GiveawayRepository giveawayRepository;
    private final RabbitMQService rabbitMQService;
    private final ShardManager shardManager;

    private final LoadingCache<String, Boolean> standaloneCache;

    public StandaloneWorkerManager(RedisTemplate<String, String> redisTemplate,
        GiveawayRepository giveawayRepository, @Lazy RabbitMQService rabbitMQService,
        ShardManager shardManager) {
        this.redisTemplate = redisTemplate;
        this.setOperations = redisTemplate.opsForSet();
        this.zSetOperations = redisTemplate.opsForZSet();
        this.giveawayRepository = giveawayRepository;
        this.rabbitMQService = rabbitMQService;
        this.shardManager = shardManager;

        this.standaloneCache = CacheBuilder.newBuilder().maximumSize(1000).build(
            new CacheLoader<>() {
                @Override
                public Boolean load(@NotNull String key) throws Exception {
                    Boolean isMember = setOperations.isMember(STANDALONE_KEY, key);
                    return Objects.requireNonNullElse(isMember, false);
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
        giveaways.forEach(this::sendToWorker);
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
        giveaways.forEach(this::removeFromWorker);
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
        log.debug("Assigning giveaway to worker {}", giveaway);
        rabbitMQService.sendToWorker(giveaway);
    }

    @Override
    public void removeFromWorker(GiveawayEntity giveaway) {
        log.debug("Removing giveaway {} from the workers", giveaway.getId());
        rabbitMQService.removeFromWorker(giveaway);
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
    public void onAllShardStart(AllShardsReadyEvent event) {
        shardManager.getGuilds().stream().filter(this::isStandalone)
            .forEach(this::distributeUntrackedGiveaways);
    }
}
