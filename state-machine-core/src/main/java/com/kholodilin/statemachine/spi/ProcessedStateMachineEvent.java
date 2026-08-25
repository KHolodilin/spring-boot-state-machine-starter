package com.kholodilin.statemachine.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * History row stored in {@code state_machine_event}.
 *
 * @param eventId     idempotency key
 * @param machineType definition name
 * @param machineId   instance identifier
 * @param eventType   event enum name
 * @param fromState   state before the outcome; for rejected events equals {@code toState}
 * @param toState     state after the outcome
 * @param result      {@code success} or {@code rejected}
 * @param createdAt   insert time
 */
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

    /**
     * Validates required fields.
     */
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
