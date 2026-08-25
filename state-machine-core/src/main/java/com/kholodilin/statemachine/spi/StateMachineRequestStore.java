package com.kholodilin.statemachine.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable async intake ({@code state_machine_request}). The in-memory queue is only a fast path.
 */
public interface StateMachineRequestStore {

    /**
     * Inserts a request and returns the generated primary key.
     *
     * @param request row with {@code id} typically {@code null}
     * @return generated {@code id}
     */
    long append(StateMachineRequest request);

    /**
     * @param id primary key
     * @return empty when the row does not exist
     */
    Optional<StateMachineRequest> findById(long id);

    /**
     * @param eventId idempotency key
     * @return the existing request when {@code sendAsync} races on the same event
     */
    Optional<StateMachineRequest> findByEventId(String eventId);

    /**
     * Rows that recovery may re-offer: {@code NEW}, {@code FAILED}, expired {@code PROCESSING}.
     *
     * @param batchSize maximum rows
     * @return oldest-first slice
     */
    List<StateMachineRequest> findRecoverable(int batchSize);

    /**
     * Claims a batch with {@code FOR UPDATE SKIP LOCKED} so two pods do not recover the same rows.
     * Sets {@code PROCESSING} and a lease; call {@link #clearLease(List)} before offering to the worker queue.
     *
     * @param lockedBy    this process {@code instance-id}
     * @param lockedUntil lease expiry
     * @param batchSize   maximum rows
     * @return claimed rows
     */
    List<StateMachineRequest> claimRecoverable(String lockedBy, Instant lockedUntil, int batchSize);

    /**
     * Drops the recovery lease so a worker can {@link #claim(long, String, Instant)} the row.
     *
     * @param ids request primary keys; no-op when empty
     */
    void clearLease(List<Long> ids);

    /**
     * Marks one request {@code PROCESSING} if it is still eligible.
     *
     * @param id          request id
     * @param lockedBy    this process {@code instance-id}
     * @param lockedUntil lease expiry
     * @return {@code true} if this worker won the claim
     */
    boolean claim(long id, String lockedBy, Instant lockedUntil);

    /**
     * @param id request that finished through {@code send()}
     */
    void markDone(long id);

    /**
     * @param id         request that threw
     * @param retryCount updated retry counter
     */
    void markFailed(long id, int retryCount);

    /**
     * Terminal failure after {@code max-retries}.
     *
     * @param id request id
     */
    void markDead(long id);

    /**
     * @return oldest {@code created_at} among non-terminal requests, or {@code null}
     */
    Instant oldestPendingCreatedAt();
}
