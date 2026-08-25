package com.kholodilin.statemachine;

import java.util.Map;
import java.util.Optional;

/**
 * Typed workflow context. Persistence stores a {@link Map}; engine code uses this wrapper.
 */
public interface StateMachineContext {

    /**
     * @param key context entry
     * @return string form of the value, empty if missing
     */
    Optional<String> getString(String key);

    /**
     * @param key context entry
     * @return integer value when the stored object is a {@link Number}
     */
    Optional<Integer> getInt(String key);

    /**
     * @param key context entry
     * @return long value when the stored object is a {@link Number}
     */
    Optional<Long> getLong(String key);

    /**
     * @param key context entry
     * @return boolean or parsed string; empty for other types
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * @param key  context entry
     * @param type expected runtime type
     * @return the value when it is an instance of {@code type}
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * Stores or replaces a value. Guards and updaters must stay pure (no I/O).
     *
     * @param key   context entry
     * @param value persisted as JSONB
     * @return this context for chaining
     */
    StateMachineContext put(String key, Object value);

    /**
     * @param key entry to drop
     * @return this context for chaining
     */
    StateMachineContext remove(String key);

    /**
     * Snapshot for persistence. Not a public mutator.
     */
    Map<String, Object> asMap();
}
