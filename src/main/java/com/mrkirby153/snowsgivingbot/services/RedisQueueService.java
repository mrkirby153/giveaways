package com.mrkirby153.snowsgivingbot.services;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for processing the queues of entrants in redis
 */
public interface RedisQueueService {


    /**
     * Processes the queue and removes the given amount of users from the giveaway
     *
     * @param giveaway The giveaway id
     * @param amount   The amount of users to process
     *
     * @return A list of users to process, or an empty list if there are none
     */
    List<String> processQueue(long giveaway, int amount);

    /**
     * Gets the depth of the queue for the given giveaway
     *
     * @param giveaway The giveaway
     *
     * @return The depth of the queue.
     */
    long queueSize(long giveaway);

    /**
     * Gets the depth of all the queues
     *
     * @return A map of queue names to their depth
     */
    Map<String, Long> allQueues();

    /**
     * Update the worker settings
     *
     * @param workerDelay The delay between worker checks
     * @param batchSize   The batch size to pull
     */
    void updateWorkers(int workerDelay, int batchSize);

    /**
     * Updates the worker count
     *
     * @param count The count of workers
     */
    void updateWorkerCount(long count);

    long getWorkerCount();

    long getWorkerDelay();

    long getBatchSize();
}
