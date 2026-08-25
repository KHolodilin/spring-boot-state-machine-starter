package com.kholodilin.statemachine.cache;

import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.spi.StateMachineCache;

import java.util.Optional;

/**
 * Cache used when {@code state-machine.cache.enabled} is {@code false}. Every lookup is a miss.
 */
public final class NoOpStateMachineCache implements StateMachineCache {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<StateMachineInstance> get(String machineType, String machineId) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(StateMachineInstance instance) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidate(String machineType, String machineId) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        return 0;
    }
}
