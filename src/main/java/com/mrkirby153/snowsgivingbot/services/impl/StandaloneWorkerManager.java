package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity.GiveawayState;
import com.mrkirby153.snowsgivingbot.entity.repo.GiveawayRepository;
import com.mrkirby153.snowsgivingbot.services.RedisQueueService;
import com.mrkirby153.snowsgivingbot.services.StandaloneWorkerService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

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

    public StandaloneWorkerManager(RedisTemplate<String, String> redisTemplate,
        GiveawayRepository giveawayRepository, @Lazy RedisQueueService redisQueueService) {
        this.redisTemplate = redisTemplate;
        this.setOperations = redisTemplate.opsForSet();
        this.zSetOperations = redisTemplate.opsForZSet();
        this.giveawayRepository = giveawayRepository;
        this.redisQueueService = redisQueueService;
    }

    @Override
    public void enableStandaloneWorker(Guild guild) {
        log.info("Enabling standalone mode for {} ({})", guild.getName(), guild.getId());
        setOperations.add(STANDALONE_KEY, guild.getId());
        List<GiveawayEntity> giveaways = giveawayRepository
            .findAllByGuildIdAndState(guild.getId(), GiveawayState.RUNNING);
        log.debug("Assigning {} giveaways to workers", giveaways.size());
        giveaways.forEach(this::sendToWorker);
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

    }

    @Override
    public boolean isStandalone(Guild guild) {
        return isStandalone(guild.getId());
    }

    @Override
    public boolean isStandalone(String id) {
        Boolean isMember = setOperations.isMember(STANDALONE_KEY, id);
        if (isMember == null) {
            return false;
        }
        return isMember;
    }

    @Override
    public void sendToWorker(GiveawayEntity giveaway) {
        String workerId = getWorker();
        log.debug("Sending giveaway {} to worker {}", giveaway.getId(), workerId);
        redisTemplate.convertAndSend(String.format(GIVEAWAY_WORKER_FORMAT, workerId),
            String.format("load:%d", giveaway.getId()));
    }

    @Override
    public void removeFromWorker(GiveawayEntity giveaway) {
        log.debug("Removing giveaway {} from the workers", giveaway.getId());
        redisTemplate.convertAndSend(GIVEAWAY_TOPIC, String.format("unload:%d", giveaway.getId()));
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
