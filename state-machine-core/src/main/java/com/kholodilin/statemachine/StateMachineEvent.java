package com.kholodilin.statemachine;

import java.util.Objects;

/**
 * Incoming event. {@code eventId} is the idempotency key and should be a globally unique UUID.
 *
 * @param eventId   unique event instance identifier
 * @param machineId target machine instance
 * @param type      what happened
 * @param payload   event-specific data; {@code null} when the payload type is {@link Void}
 */
public record StateMachineEvent<E, P>(
        String eventId,
        String machineId,
        E type,
        P payload
) {

    /**
     * Rejects null or blank identifiers.
     */
    public StateMachineEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(type, "type");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (machineId.isBlank()) {
            throw new IllegalArgumentException("machineId must not be blank");
        }
    }
}
