package com.mrkirby153.snowsgivingbot.services;

import com.mrkirby153.snowsgivingbot.entity.GiveawayEntity;

/**
 * Service for handling migrations to giveaway embeds
 */
public interface GiveawayMigrationService {

    /**
     * Gets the version of this giveaway
     *
     * @param messageId The message id
     *
     * @return The version of the giveaway
     */
    long getVersion(String messageId);

    /**
     * Migrates the giveaway entity
     *
     * @param entity The entity to migrate
     */
    void doMigration(GiveawayEntity entity);
}
