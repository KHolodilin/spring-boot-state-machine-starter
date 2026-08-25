package com.kholodilin.statemachine.spi;

import java.util.Optional;

/**
 * Durable event history and idempotency store ({@code state_machine_event}).
 */
public interface StateMachineEventStore {

    /**
     * @param eventId idempotency key
     * @return {@code true} if this event was already appended
     */
    boolean exists(String eventId);

    /**
     * @param eventId idempotency key
     * @return the stored history row, including rejected outcomes
     */
    Optional<ProcessedStateMachineEvent> find(String eventId);

    /**
     * Inserts a history row. Unique {@code event_id} is required.
     *
     * @param event success or rejected snapshot
     */
    void append(ProcessedStateMachineEvent event);
}
