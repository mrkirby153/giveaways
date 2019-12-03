package com.mrkirby153.tgabot.services;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;

/**
 * Helper for various discord-based commands
 */
public interface DiscordService {

    /**
     * Finds an emote by its id in any of the guilds that the bot is a part of
     *
     * @param id The id of the emote
     *
     * @return The emote
     *
     * @throws java.util.NoSuchElementException If there was no emote found
     */
    Emote findEmoteById(String id);


    /**
     * Finds a message by its jump link
     *
     * @param jumpLink The jump link of the message
     *
     * @return The message
     *
     * @throws java.util.NoSuchElementException If the message, guild, or channel was not found
     * @throws IllegalArgumentException         If a non-jump link was provided
     */
    Message findMessage(String jumpLink);

    /**
     * Finds a message by its id. If a jump link is passed then
     * {@link DiscordService#findMessage(String)} is used instead
     *
     * @param guild   The guild to look in
     * @param message The message
     *
     * @return The message
     *
     * @throws java.util.NoSuchElementException If the message was not found
     */
    Message findMessageById(Guild guild, String message);
}
