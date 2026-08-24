package com.kholodilin.statemachine;

import java.util.Map;
import java.util.Optional;

/**
 * Typed workflow context. Persistence stores a {@link Map}; engine code uses this wrapper.
 */
public interface StateMachineContext {

    Optional<String> getString(String key);

    Optional<Integer> getInt(String key);

    Optional<Long> getLong(String key);

    Optional<Boolean> getBoolean(String key);

    <T> Optional<T> get(String key, Class<T> type);

    StateMachineContext put(String key, Object value);

    StateMachineContext remove(String key);

    /**
     * Snapshot for persistence. Not a public mutator.
     */
    Map<String, Object> asMap();
}
