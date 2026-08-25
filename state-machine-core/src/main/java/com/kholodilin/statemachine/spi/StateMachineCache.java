package com.kholodilin.statemachine.spi;

import com.kholodilin.statemachine.StateMachineInstance;

import java.util.Optional;

/**
 * Optional RAM hot set. PostgreSQL remains the source of truth. Eviction never persists.
 */
public interface StateMachineCache {

    /**
     * @param machineType definition name
     * @param machineId   instance identifier
     * @return cached snapshot when present
     */
    Optional<StateMachineInstance> get(String machineType, String machineId);

    /**
     * Stores or replaces the snapshot after a successful persist.
     *
     * @param instance durable state
     */
    void put(StateMachineInstance instance);

    /**
     * Drops an entry, typically after an optimistic-lock conflict.
     *
     * @param machineType definition name
     * @param machineId   instance identifier
     */
    void invalidate(String machineType, String machineId);

    /**
     * @return estimated number of cached instances
     */
    long size();
}
