package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Option;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.springframework.context.event.EventListener;

public interface PollReactionService {

    @EventListener
    void onReact(MessageReactionAddEvent event);

    /**
     * Records a vote for the given user
     *
     * @param category The category
     * @param option   The option
     * @param userId   The user
     */
    void recordVote(Category category, Option option, String userId, boolean updateMessage);

    /**
     * Process any outstanding votes on a category
     *
     * @param category The category
     */
    void processOutstanding(Category category);

    /**
     * Gets the threshold for clearning all reactions and adding new ones
     *
     * @return The threshold
     */
    int getThreshold();

    /**
     * Sets the threshold for clearing all reactions and adding new ones
     *
     * @param threshold The threshold
     */
    void setThreshold(int threshold);
}
