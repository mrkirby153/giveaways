package com.mrkirby153.tgabot.services;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import java.util.concurrent.CompletableFuture;

/**
 * Service for managing reactions and dealing with the associated ratelimits
 */
public interface ReactionService {

    /**
     * Removes a reaction by the given user from the provided message
     *
     * @param message  The message to remove the reactions from
     * @param user     The user to remove the reaction from
     * @param reaction The reaction to remove
     *
     * @return A completable future of the reaction result
     */
    CompletableFuture<Result> removeReaction(Message message, User user, String reaction);

    /**
     * Removes a reaction by the given user from the provided message
     *
     * @param message The message to remove the reactions from
     * @param user    The user to remove the reaction from
     * @param emote   The emote to remove
     *
     * @return A completable future of the reaction result
     */
    CompletableFuture<Result> removeReaction(Message message, User user, Emote emote);

    /**
     * Removes all reactions by the given user from the provided message
     *
     * @param message The message to remove the reactions from
     *
     * @return A completable future of the reaction result
     */
    CompletableFuture<Result> removeAllReactions(Message message);

    /**
     * The amount of pending removal tasks
     *
     * @param message The message
     *
     * @return The amount of reactions pending removal on a message
     */
    long pendingRemovals(Message message);

    /**
     * Resizes the thread pool running tasks
     *
     * @param newSize The new size of the threadpool
     */
    void resizeThreadPool(int newSize);

    /**
     * The result of a reaction event
     */
    enum Result {
        /**
         * If the operation completed successfully
         */
        SUCCESS,

        /**
         * If the operation was aborted
         */
        ABORTED,

        /**
         * If no operation was performed
         */
        NO_OP;
    }
}
