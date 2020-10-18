package com.mrkirby153.snowsgivingbot.services.setting.settings;

import lombok.NonNull;

/**
 * A {@link Long} setting
 */
public class LongSetting extends AbstractSetting<Long> {


    public LongSetting(@NonNull String key, Long defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public Long deserialize(String json) {
        return Long.parseLong(json);
    }

    @Override
    public String serialize(Long obj) {
        return obj.toString();
    }
}
