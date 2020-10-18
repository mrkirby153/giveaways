package com.mrkirby153.snowsgivingbot.services.setting.settings;

import lombok.NonNull;

/**
 * A boolean setting
 */
public class BooleanSetting extends AbstractSetting<Boolean> {

    public BooleanSetting(@NonNull String key, Boolean defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public Boolean deserialize(String json) {
        if (json.equalsIgnoreCase("true")) {
            return true;
        } else if (json.equalsIgnoreCase("false")) {
            return false;
        } else {
            throw new IllegalArgumentException("Invalid boolean value provided");
        }
    }

    @Override
    public String serialize(Boolean obj) {
        return obj.toString();
    }
}
