package com.kholodilin.statemachine.spi;

import com.kholodilin.statemachine.StateMachineInstance;

import java.util.Optional;

public interface StateMachineStore {

    Optional<StateMachineInstance> find(String machineType, String machineId);

    StateMachineInstance create(StateMachineInstance instance);

    boolean update(StateMachineInstance instance, long expectedVersion);
}
