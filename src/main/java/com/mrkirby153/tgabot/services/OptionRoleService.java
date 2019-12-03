package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.events.VoteCastEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * Option role service
 */
public interface OptionRoleService {

    /**
     * Event handler for when a vote is cast
     *
     * @param event The event
     */
    @EventListener
    @Async
    void onVoteCast(VoteCastEvent event);

    /**
     * Syncs all user roles with options
     */
    void syncRoles();

    /**
     * Refreshes the option role memory cache
     */
    void refreshOptionRoleCache();
}
