package com.kholodilin.statemachine.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable async request row.
 *
 * @param id           generated primary key; {@code null} before insert
 * @param eventId      idempotency key
 * @param machineType  definition name
 * @param machineId    instance identifier
 * @param eventType    event enum name
 * @param payloadJson  JSON serialized with the registered payload class
 * @param status       {@link #NEW}, {@link #PROCESSING}, {@link #FAILED}, {@link #DONE} or {@link #DEAD}
 * @param retryCount   failed processing attempts
 * @param lockedBy     process that holds the PROCESSING lease
 * @param lockedUntil  lease expiry
 * @param createdAt    insert time
 * @param processedAt  terminal completion time
 */
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

    /** Not yet claimed by a worker. */
    public static final int NEW = 0;

    /** A worker holds a lease. */
    public static final int PROCESSING = 1;

    /** Processing failed; recovery may retry. */
    public static final int FAILED = 2;

    /** Transition finished (success, duplicate or rejected). */
    public static final int DONE = 100;

    /** Exhausted {@code max-retries}. */
    public static final int DEAD = 101;

    /**
     * Validates required identity fields.
     */
    public StateMachineRequest {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(eventType, "eventType");
    }
}
