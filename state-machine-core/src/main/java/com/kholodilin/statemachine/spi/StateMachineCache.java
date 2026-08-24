package com.kholodilin.statemachine.spi;

import com.kholodilin.statemachine.StateMachineInstance;

import java.util.Optional;

public interface StateMachineCache {

    Optional<StateMachineInstance> get(String machineType, String machineId);

    void put(StateMachineInstance instance);

    void invalidate(String machineType, String machineId);

    long size();
}
