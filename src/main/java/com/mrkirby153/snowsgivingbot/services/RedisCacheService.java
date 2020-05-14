package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import com.mrkirby153.snowsgivingbot.entity.GiveawayEntrantEntity;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Map;

public interface RedisCacheService {


    /**
     * Loads the entry into the cache
     *
     * @param giveawayEntity The giveway to load
     */
    void loadIntoCache(GiveawayEntity giveawayEntity);

    /**
     * Cache the users in redis
     *
     * @param giveaway The giveway to cache the user for
     * @param user     The user
     */
    void cacheUser(GiveawayEntity giveaway, String user);

    /**
     * Checks if the user has entered the giveaway
     *
     * @param user     The user
     * @param giveaway The giveaway
     *
     * @return True if the user has entered a giveaway
     */
    boolean entered(User user, GiveawayEntity giveaway);

    /**
     * Queues an entrant into the cache
     *
     * @param giveawayEntity The giveaway to queue
     * @param user           The user to queue
     */
    void queueEntrant(GiveawayEntity giveawayEntity, User user);

    /**
     * Converts the queue into GiveawayEntrantEntities to be saved in the database
     *
     * @param giveawayEntity The giveaway
     * @param amount         The amount of items to process
     *
     * @return The items
     */
    List<GiveawayEntrantEntity> processQueue(GiveawayEntity giveawayEntity, long amount);

    /**
     * Checks the queue size of stuff to be processed
     *
     * @param giveawayEntity The giveaway
     *
     * @return The queue size
     */
    long queueSize(GiveawayEntity giveawayEntity);

    /**
     * Gets the total queue size for all giveaways
     *
     * @return The total queue size
     */
    long queueSize();

    Map<String, Long> allQueues();

    /**
     * Uncaches data in the database
     *
     * @param giveawayEntity The entity to uncache
     */
    void uncache(GiveawayEntity giveawayEntity);

    /**
     * Updates the worker settings
     *
     * @param batchSize  The batch size
     * @param sleepDelay The sleep delay
     */
    void updateWorkerSettings(int batchSize, int sleepDelay);

    boolean isStandalone();

    void setStandalone(boolean standalone);
}
