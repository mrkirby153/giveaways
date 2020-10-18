package com.mrkirby153.snowsgivingbot.services.setting;

/**
 * A guild setting
 */
public interface GuildSetting<T> {

    /**
     * Deserializes the json into the given type
     *
     * @param json The json to deserialize
     *
     * @return The deserialized data
     */
    T deserialize(String json);

    /**
     * Gets the default value of the setting
     *
     * @return The default value of the setting
     */
    T getDefaultSetting();

    /**
     * Gets the key of the setting
     *
     * @return The key
     */
    String getKey();

    /**
     * Serializes the object into the given type
     *
     * @param obj The object to serialize
     *
     * @return The JSON representation of the object
     */
    String serialize(T obj);
}
