package com.mrkirby153.snowsgivingbot.services.setting;

import net.dv8tion.jda.api.entities.Guild;

public interface SettingService {

    void set(GuildSetting<?> setting, Guild guild, Object value);

    void set(GuildSetting<?> setting, String guildId, Object value);

    <T> T get(GuildSetting<T> setting, Guild guild);

    <T> T get(GuildSetting<T> setting, String guildId);

    void reset(GuildSetting<?> setting, Guild guild);

    void reset(GuildSetting<?> setting, String  guildId);
}
