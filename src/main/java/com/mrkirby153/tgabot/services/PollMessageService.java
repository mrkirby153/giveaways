package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.Category;
import com.mrkirby153.tgabot.entity.Vote;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing poll messages
 */
public interface PollMessageService {


    /**
     * Updates the discord message.
     *
     * This includes:
     * <ul>
     *     <li>(Re)Sending the message</li>
     *     <li>Updating the message's options</li>
     *     <li>Updating the message's reactions</li>
     * </ul>
     *
     * @param category The category
     */
    void updatePollMessage(Category category);

    /**
     * Updates the discord message. This should only be used if it's impossible to get a transaction (i.e. in callbacks)
     *
     * @param categoryId The category id
     */
    @Transactional
    void updatePollMessage(long categoryId);

    /**
     * Updates the discord message debounced at once per minute
     *
     * @param category The category to update
     *
     * @see PollMessageService#updatePollMessage(Category)
     */
    void updatePollMessageDebounced(Category category);

    /**
     * Gets a list of outstanding votes (has reacted, but not tallied)
     *
     * @param category The category
     *
     * @return A list of outstanding votes
     */
    CompletableFuture<List<Vote>> getOutstandingVotes(Category category);

    /**
     * Updates the reactions on a category. If the message has not been sent, this is a noop
     *
     * @param category The category to update reactions on
     */
    void updateReactions(Category category);

    /**
     * Removes a category's message
     *
     * @param category The category
     */
    void removeCategory(Category category);
}
