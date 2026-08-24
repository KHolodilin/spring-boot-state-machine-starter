package com.kholodilin.statemachine;

import java.util.Objects;

/**
 * Acknowledgement that an async event was durably accepted.
 */
public record AsyncSubmission(
        String eventId,
        String machineType,
        String machineId,
        long requestId
) {

    public AsyncSubmission {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
    }
}
