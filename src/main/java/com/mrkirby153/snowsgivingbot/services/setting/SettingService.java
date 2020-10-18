package com.mrkirby153.snowsgivingbot.services.setting;

import net.dv8tion.jda.api.entities.Guild;

public interface SettingService {

    <T> void set(GuildSetting<T> setting, Guild guild, T value);

    <T> void set(GuildSetting<T> setting, String guildId, T value);

    <T> T get(GuildSetting<T> setting, Guild guild);

    <T> T get(GuildSetting<T> setting, String guildId);

    void reset(GuildSetting<?> setting, Guild guild);

    void reset(GuildSetting<?> setting, String  guildId);
}
