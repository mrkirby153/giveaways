package com.mrkirby153.snowsgivingbot.services.setting.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ArraySetting<T> extends AbstractSetting<List<T>> {

    private final Class<T> clazz;

    public ArraySetting(@NonNull String key, Class<T> clazz) {
        super(key, null);
        this.clazz = clazz;
    }

    @Override
    public List<T> deserialize(String json) {
        ObjectMapper mapper = ObjectSetting.objectMapper;
        CollectionType type = mapper.getTypeFactory().constructCollectionType(List.class, clazz);
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize {}", json, e);
        }
        return null;
    }

    @Override
    public String serialize(Object obj) {
        try {
            return ObjectSetting.objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize {}", obj, e);
        }
        return null;
    }
}
