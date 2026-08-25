package com.kholodilin.statemachine.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import com.kholodilin.statemachine.exception.PayloadDeserializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 helpers for workflow context maps and event payloads stored as JSONB.
 */
public final class JsonMaps {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JsonMapper mapper;

    /**
     * @param mapper application {@link JsonMapper}
     */
    public JsonMaps(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param value object or {@code null}
     * @return JSON string, or {@code null} when {@code value} is {@code null}
     */
    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new PayloadDeserializationException("Failed to serialize JSON", ex);
        }
    }

    /**
     * @param json JSON object text
     * @return empty map when {@code json} is blank
     */
    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            LinkedHashMap<String, Object> map = mapper.readValue(json, MAP_TYPE);
            return map == null ? Map.of() : map;
        } catch (JacksonException ex) {
            throw new PayloadDeserializationException("Failed to deserialize context JSON", ex);
        }
    }

    /**
     * @param json JSON payload
     * @param type registered payload class; {@link Void} yields {@code null}
     * @return deserialized value or {@code null}
     */
    public <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank() || type == Void.class) {
            return null;
        }
        try {
            return mapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new PayloadDeserializationException("Failed to deserialize payload as " + type.getName(), ex);
        }
    }
}
