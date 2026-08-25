package com.kholodilin.statemachine.spi;

import com.kholodilin.statemachine.StateMachineInstance;

import java.util.Optional;

/**
 * Durable store for instance snapshots ({@code state_machine_instance}).
 */
public interface StateMachineStore {

    /**
     * @param machineType definition name
     * @param machineId   instance identifier
     * @return empty when the instance has not been created yet
     */
    Optional<StateMachineInstance> find(String machineType, String machineId);

    /**
     * Inserts a new instance. If the row already exists, implementations return the stored snapshot.
     *
     * @param instance initial state, typically the definition's initial state and version {@code 0}
     * @return the persisted instance
     */
    StateMachineInstance create(StateMachineInstance instance);

    /**
     * Optimistic update: succeeds only when {@code version} still matches {@code expectedVersion}.
     *
     * @param instance        new state, context and expected next version
     * @param expectedVersion version currently believed to be in the database
     * @return {@code true} if exactly one row was updated
     */
    boolean update(StateMachineInstance instance, long expectedVersion);
}
