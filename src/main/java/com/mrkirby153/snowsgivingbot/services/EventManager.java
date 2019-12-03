package com.mrkirby153.snowsgivingbot.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class EventManager implements EventService, EventListener {

    private final ApplicationEventPublisher publisher;

    @Override
    public void onEvent(GenericEvent event) {
        log.debug("Relaying {} to spring", event);
        publisher.publishEvent(event);
    }
}
