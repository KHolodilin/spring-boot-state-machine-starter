package com.kholodilin.statemachine.spi;

import java.time.Instant;
import java.util.Objects;

public record StateMachineRequest(
        Long id,
        String eventId,
        String machineType,
        String machineId,
        String eventType,
        String payloadJson,
        int status,
        int retryCount,
        String lockedBy,
        Instant lockedUntil,
        Instant createdAt,
        Instant processedAt
) {

    public static final int NEW = 0;
    public static final int PROCESSING = 1;
    public static final int FAILED = 2;
    public static final int DONE = 100;
    public static final int DEAD = 101;

    public StateMachineRequest {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(eventType, "eventType");
    }
}
