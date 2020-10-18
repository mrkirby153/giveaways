package com.mrkirby153.snowsgivingbot.services.setting.settings;

import lombok.NonNull;

/**
 * A {@link Double} setting
 */
public class DoubleSetting extends AbstractSetting<Double> {


    public DoubleSetting(@NonNull String key, Double defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public Double deserialize(String json) {
        return Double.parseDouble(json);
    }

    @Override
    public String serialize(Double obj) {
        return obj.toString();
    }
}
