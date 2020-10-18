package com.mrkirby153.snowsgivingbot.services.setting.settings;

import com.mrkirby153.snowsgivingbot.services.setting.GuildSetting;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * An abstract setting
 * @param <T>
 */
@RequiredArgsConstructor
public abstract class AbstractSetting<T> implements GuildSetting<T> {

    @NonNull
    private final String key;
    private final T defaultValue;

    @Override
    public T getDefaultSetting() {
        return defaultValue;
    }

    @Override
    public String getKey() {
        return key;
    }
}
