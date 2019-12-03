package com.mrkirby153.tgabot.services;

import net.dv8tion.jda.api.entities.User;

/**
 * Handles the vote confirmation channel as well as giving the voted role to users
 */
public interface VoteConfirmationService {

    /**
     * Adds the voted role to the user
     *
     * @param user The user
     */
    void addVotedRoleToUser(User user);

    /**
     * Removes the voted role from the user
     *
     * @param user The user
     */
    void removeVotedRoleFromUser(User user);

    /**
     * Synchronizes the voted users role with those who have previously voted
     */
    void syncVotedUsers();

    /**
     * Refreshes the voted message
     */
    void refreshVotedMessage();
}
