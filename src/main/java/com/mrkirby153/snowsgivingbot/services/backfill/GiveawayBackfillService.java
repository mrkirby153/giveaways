package com.mrkirby153.snowsgivingbot.services.backfill;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;

import java.util.List;
import java.util.Set;

/**
 * Service managing the backfill of giveaway reactions from Discord
 */
public interface GiveawayBackfillService {


    /**
     * Stats the backfill of a giveaway
     *
     * @param giveaway The giveaway to backfill
     *
     * @return A backfill task
     */
    BackfillTask startBackfill(GiveawayEntity giveaway);

    /**
     * Gets a list of running backfill tasks
     */
    List<BackfillTask> getRunning();

    /**
     * Gets a list of running giveaway IDs
     *
     * @return A set of running giveaways
     */
    Set<Long> getRunningGiveawayIDs();

    /**
     * Cancels the backfill of a giveaway
     *
     * @param task The task
     */
    void unregisterTask(BackfillTask task);
}
