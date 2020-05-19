package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;
import net.dv8tion.jda.api.entities.Guild;

/**
 * Service handling the coordination of giveaways running on standalone workers
 */
public interface StandaloneWorkerService {

    /**
     * Puts a guild into standalone worker mode. In this mode, the main bot will no longer handle
     * the processing of reaction events but instead rely on a separate worker process to handle
     * queueing of event.
     *
     * @param guild The guild to enable standalone worker mode on
     */
    void enableStandaloneWorker(Guild guild);

    /**
     * Removes a guild from standalone worker mode. The main bot process will begin handling the
     * processing of reaction events again.
     *
     * @param guild THe guild to disable standalone worker mode on
     */
    void disableStandaloneWorker(Guild guild);

    /**
     * Checks if a guild is operating in standalone worker mode
     *
     * @param guild The guild to check
     *
     * @return True if the guild is in standalone worker mode, false if it is not
     */
    boolean isStandalone(Guild guild);

    /**
     * Checks if a guild is operating in standalone worker mode
     *
     * @param id The guild id to check
     *
     * @return True if the guild is in standalone worker mode
     */
    Boolean isStandalone(String id);

    /**
     * Sends a giveaway to the worker with the lowest load
     *
     * @param giveaway The giveaway to send
     */
    void sendToWorker(GiveawayEntity giveaway);

    /**
     * Removes the giveaway from the workers. The giveaway will immediately stop being processed by
     * the worker.
     *
     * @param giveaway The giveaway to remove
     */
    void removeFromWorker(GiveawayEntity giveaway);
}
