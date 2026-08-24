package com.kholodilin.statemachine.spi;

import java.time.Instant;
import java.util.Objects;

public record ProcessedStateMachineEvent(
        String eventId,
        String machineType,
        String machineId,
        String eventType,
        String fromState,
        String toState,
        String result,
        Instant createdAt
) {

    public ProcessedStateMachineEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(fromState, "fromState");
        Objects.requireNonNull(toState, "toState");
        Objects.requireNonNull(result, "result");
    }
}
