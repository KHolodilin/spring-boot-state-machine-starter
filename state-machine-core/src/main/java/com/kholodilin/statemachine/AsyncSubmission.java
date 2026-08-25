package com.kholodilin.statemachine;

import java.util.Objects;

/**
 * Acknowledgement that an async event was durably accepted.
 *
 * @param eventId     idempotency key
 * @param machineType definition name
 * @param machineId   instance identifier
 * @param requestId   generated {@code state_machine_request.id}
 */
public record AsyncSubmission(String eventId, String machineType, String machineId, long requestId) {

    /**
     * Validates required identifiers.
     */
    public AsyncSubmission {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
    }
}
