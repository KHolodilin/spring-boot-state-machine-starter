package com.kholodilin.statemachine.persistence;

import com.kholodilin.statemachine.exception.PayloadDeserializationException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonMaps {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JsonMapper mapper;

    public JsonMaps(JsonMapper mapper) {
        this.mapper = mapper;
    }

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
