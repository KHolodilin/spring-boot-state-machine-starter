package com.kholodilin.statemachine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mutable {@link Map}-backed context. {@link #getInt(String)} and {@link #getLong(String)}
 * accept any {@link Number} after JSONB deserialization.
 */
public final class MapStateMachineContext implements StateMachineContext {

    private final Map<String, Object> values;

    public MapStateMachineContext(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values == null ? Map.of() : values);
    }

    public static MapStateMachineContext empty() {
        return new MapStateMachineContext(Map.of());
    }

    public static MapStateMachineContext copyOf(Map<String, Object> values) {
        return new MapStateMachineContext(values);
    }

    @Override
    public Optional<String> getString(String key) {
        Object value = values.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }

    @Override
    public Optional<Integer> getInt(String key) {
        return number(key).map(Number::intValue);
    }

    @Override
    public Optional<Long> getLong(String key) {
        return number(key).map(Number::longValue);
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return Optional.of(bool);
        }
        if (value instanceof String text) {
            return Optional.of(Boolean.parseBoolean(text));
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = values.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    @Override
    public StateMachineContext put(String key, Object value) {
        Objects.requireNonNull(key, "key");
        values.put(key, value);
        return this;
    }

    @Override
    public StateMachineContext remove(String key) {
        values.remove(key);
        return this;
    }

    @Override
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private Optional<Number> number(String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return Optional.of(number);
        }
        return Optional.empty();
    }
}
