package com.mrkirby153.snowsgivingbot.services.setting.settings;

import lombok.NonNull;

/**
 * A {@link String} setting
 */
public class StringSetting extends AbstractSetting<String> {

    public StringSetting(@NonNull String key, String defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public String deserialize(String json) {
        return json;
    }

    @Override
    public String serialize(Object obj) {
        return obj.toString();
    }
}
