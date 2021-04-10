package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Map;

public interface RabbitMQService {

    /**
     * Sends the provided giveaway to a worker
     *
     * @param entity The giveaway to send
     */
    void sendToWorker(GiveawayEntity entity);

    /**
     * Removes the provided giveaway from its worker
     *
     * @param entity The entity to remove
     */
    void removeFromWorker(GiveawayEntity entity);

    /**
     * Changes the prefetch count for the worker queue listeners
     *
     * @param newCount The new prefetch count
     */
    void updatePrefetchCount(int newCount);

    /**
     * Creates and starts all queue handlers for a guild
     *
     * @param guild The guild
     */
    void startAll(Guild guild);

    /**
     * Gets the current processing queue size for the provided Giveaway
     *
     * @param entity The entity
     *
     * @return The queue size
     */
    long queueSize(GiveawayEntity entity);

    /**
     * Gets the current processing queue size for the provided giveaway id
     *
     * @param giveawayId The giveaway id to get the queue size for
     *
     * @return The queue size
     */
    long queueSize(long giveawayId);

    /**
     * Gets the running queue sizes for all provided giveaways
     *
     * @return The giveaways and their queue sizes
     */
    Map<Long, Long> runningQueueSizes();
}
