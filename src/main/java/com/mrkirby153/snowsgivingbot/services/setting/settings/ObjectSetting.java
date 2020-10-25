package com.mrkirby153.snowsgivingbot.services.setting.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * An object setting.
 *
 * Objects are serialized with the
 *
 * @param <O> The object
 */
@Slf4j
public class ObjectSetting<O> extends AbstractSetting<O> {

    private static ObjectMapper objectMapper;

    private final Class<O> clazz;

    public ObjectSetting(@NonNull String key, Class<O> clazz, O defaultValue) {
        super(key, defaultValue);
        this.clazz = clazz;
    }

    /**
     * Sets the object mapper that should be used by the object setting for object (de)serialization
     *
     * @param mapper The mapper to set
     */
    public static void setObjectMapper(ObjectMapper mapper) {
        objectMapper = mapper;
    }

    @Override
    public O deserialize(String json) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize \"{}\" to type {}", json, clazz, e);
            return null;
        }
    }

    @Override
    public String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize {}", obj, e);
            return null;
        }
    }
}
