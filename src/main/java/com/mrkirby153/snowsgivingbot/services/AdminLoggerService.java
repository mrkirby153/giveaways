package com.mrkirby153.snowsgivingbot.services;

public interface AdminLoggerService {


    /**
     * Logs a message to the configured admin log channel (if enabled)
     *
     * @param message The message to log
     */
    void log(String message);
}
