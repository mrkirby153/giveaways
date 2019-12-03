package com.mrkirby153.tgabot.services;

import com.mrkirby153.tgabot.entity.ActionLog;
import com.mrkirby153.tgabot.entity.ActionLog.ActionType;
import net.dv8tion.jda.api.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for recording action logs
 */
public interface LogService {

    /**
     * Records an action
     *
     * @param user       The user
     * @param actionType The action type
     * @param message    The message
     */
    ActionLog recordAction(User user, ActionType actionType, String message);

    /**
     * Records an action
     *
     * @param user       The user ID
     * @param actionType The action type
     * @param message    The message
     *
     * @return The message
     */
    ActionLog recordAction(String user, ActionType actionType, String message);

    /**
     * Gets all the actions for a user
     *
     * @param user     The user
     * @param pageable A pageable for paging
     *
     * @return The actions of the user
     */
    Page<ActionLog> getActions(String user, Pageable pageable);

    /**
     * Logs a message to the bot log channel
     *
     * @param message The message to log
     */
    void logMessage(String message);
}
