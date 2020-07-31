package com.mrkirby153.snowsgivingbot.services.impl;

import com.mrkirby153.botcore.event.EventWaiter;
import com.mrkirby153.snowsgivingbot.services.ConfirmationService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ConfirmationManager implements ConfirmationService {

    private static final String CHECK = "✅";
    private static final String CROSS = "❌";

    private final EventWaiter eventWaiter;

    public ConfirmationManager(EventWaiter eventWaiter) {
        this.eventWaiter = eventWaiter;
    }


    @Override
    public CompletableFuture<Boolean> confirm(Message message, User user) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        message.addReaction(CHECK).queue();
        message.addReaction(CROSS).queue();
        eventWaiter.waitFor(MessageReactionAddEvent.class,
            evt -> {
                if (evt.getMessageId().equals(message.getId())) {
                    if (evt.getUserId().equals(user.getId())) {
                        String emoji = evt.getReactionEmote().getEmoji();
                        return emoji.equals(CHECK) || emoji.equals(CROSS);
                    }
                }
                return false;
            }, evt -> {
                if (evt.getReactionEmote().getEmoji().equals(CHECK)) {
                    future.complete(true);
                } else if (evt.getReactionEmote().getEmoji()
                    .equals(CROSS)) {
                    future.complete(false);
                } else {
                    future.completeExceptionally(new IllegalStateException("Unknown reaction"));
                }
            }, 30, TimeUnit.SECONDS,
            () -> future.complete(false));
        return future;
    }
}
