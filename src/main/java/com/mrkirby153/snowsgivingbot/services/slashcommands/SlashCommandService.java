package com.mrkirby153.snowsgivingbot.services.slashcommands;

/**
 * Handle slash commands from the API
 */
public interface SlashCommandService {


    /**
     * Discover and register slash commands in the given class
     *
     * @param clazz The class to register commands in
     */
    void registerSlashCommands(Class<?> clazz);

    /**
     * Discover and register slash commands on a given instance
     *
     * @param object The object to register slash commands in
     */
    void registerSlashCommands(Object object);

    /**
     * Commits the discovered slash commands
     * If <code>bot.slash-commands.guilds</code> is set, the discovered slash commands will only
     * be committed to those guilds (delimited by a ,). Otherwise, they will be committed globally
     */
    void commitSlashCommands();
}
