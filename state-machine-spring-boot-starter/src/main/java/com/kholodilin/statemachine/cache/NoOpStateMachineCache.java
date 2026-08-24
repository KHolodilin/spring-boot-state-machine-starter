package com.kholodilin.statemachine.cache;

import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.spi.StateMachineCache;

import java.util.Optional;

public final class NoOpStateMachineCache implements StateMachineCache {

    @Override
    public Optional<StateMachineInstance> get(String machineType, String machineId) {
        return Optional.empty();
    }

    @Override
    public void put(StateMachineInstance instance) {
        // no-op
    }

    @Override
    public void invalidate(String machineType, String machineId) {
        // no-op
    }

    @Override
    public long size() {
        return 0;
    }
}
