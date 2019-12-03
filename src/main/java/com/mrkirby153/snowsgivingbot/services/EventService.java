package com.mrkirby153.snowsgivingbot.services;

import net.dv8tion.jda.api.events.GenericEvent;

/**
 * JDA to spring event manager shim
 */
public interface EventService {

    /**
     * Shim to relay a generic discord event to spring's event handling system
     *
     * @param event The event to relay
     */
    void onEvent(GenericEvent event);
}
