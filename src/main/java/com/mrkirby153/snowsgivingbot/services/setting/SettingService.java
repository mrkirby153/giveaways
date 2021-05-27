package com.mrkirby153.snowsgivingbot.services.setting;

import net.dv8tion.jda.api.entities.Guild;

public interface SettingService {

    /**
     * Sets a setting on a guild
     *
     * @param setting The setting to set
     * @param guild   The guild to set
     * @param value   The value of the setting
     */
    void set(GuildSetting<?> setting, Guild guild, Object value);

    /**
     * Sets a setting on a guild by its id
     *
     * @param setting The setting to set
     * @param guildId The id of the guild
     * @param value   The value of the setting
     */
    void set(GuildSetting<?> setting, String guildId, Object value);

    /**
     * Gets a setting from a guild
     *
     * @param setting The setting to get
     * @param guild   The guild to get the setting from
     * @param <T>     The type of setting
     *
     * @return The setting's value
     */
    <T> T get(GuildSetting<T> setting, Guild guild);

    /**
     * Gets a setting from a guild by the guild's id
     *
     * @param setting The setting to get
     * @param guildId The guild to get the setting from
     * @param <T>     The type of the setting
     *
     * @return The setting's value
     */
    <T> T get(GuildSetting<T> setting, String guildId);

    /**
     * Resets a setting on a guild to its default
     *
     * @param setting The setting to reset
     * @param guild   The guild to reset the setting on
     */
    void reset(GuildSetting<?> setting, Guild guild);

    /**
     * Resets a setting on a guild by its id to the default
     *
     * @param setting The setting to reset
     * @param guildId The guild id to reset the setting on
     */
    void reset(GuildSetting<?> setting, String guildId);


    /**
     * Opts a guild into the provided alpha
     *
     * @param alpha The alpha to add the guild to
     * @param guild The guild to opt into the alpha
     */
    void optIntoAlpha(String alpha, Guild guild);

    /**
     * Removes the user from the provided alpha
     *
     * @param alpha The alpha to remove the guild from
     * @param guild The guild to remove the alpha from
     */
    void removeFromAlpha(String alpha, Guild guild);

    /**
     * Checks if a guild is in the provided alpha
     *
     * @param alpha The alpha to check
     * @param guild The guild to check
     *
     * @return True if the guild is in the provided alpha
     */
    boolean inAlpha(String alpha, Guild guild);

    /**
     * Checks if the provided setting is an alpha setting
     *
     * @param setting The setting to check
     *
     * @return True if this setting is an alpha setting
     */
    boolean isAlpha(GuildSetting<?> setting);
}
