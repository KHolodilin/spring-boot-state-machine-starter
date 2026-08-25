package com.kholodilin.statemachine.spi;

import java.time.Duration;

/**
 * In-memory partitioned dispatch of async request ids. Routing uses {@code machineId}
 * so events for one instance stay on one worker inside a pod.
 */
public interface StateMachineDispatchQueue {

    /**
     * Enqueues {@code requestId} onto the partition derived from {@code machineId}.
     *
     * @param requestId durable request primary key
     * @param machineId routing key
     * @return {@code false} on overflow; recovery will re-offer later
     */
    boolean offer(long requestId, String machineId);

    /**
     * Blocks until an id is available on {@code partition} or {@code timeout} elapses.
     *
     * @param partition worker index
     * @param timeout   poll timeout
     * @return {@code null} on timeout
     */
    Long poll(int partition, Duration timeout) throws InterruptedException;

    /**
     * @return number of partitions / workers
     */
    int partitions();

    /**
     * @return current number of queued request ids
     */
    int size();

    /**
     * @return configured total capacity
     */
    int capacity();

    /**
     * @return {@code size / capacity}, used as a health/metrics gauge
     */
    double pressure();
}
