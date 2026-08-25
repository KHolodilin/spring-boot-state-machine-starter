package com.kholodilin.statemachine;

/**
 * Public entry point for synchronous and asynchronous event processing.
 */
public interface StateMachineService {

    /**
     * Processes the event on the caller thread under an advisory lock.
     *
     * @param machineType registered definition name
     * @param event       unique {@code eventId} is the idempotency key
     * @return {@link TransitionResult.Success}, {@link TransitionResult.Duplicate} or {@link TransitionResult.Rejected}
     */
    <E, P> TransitionResult send(String machineType, StateMachineEvent<E, P> event);

    /**
     * Inserts a durable async request and returns after commit. Workers process it later.
     *
     * @param machineType registered definition name
     * @param event       payload is serialized with the class registered via {@code .payload(...)}
     * @return acknowledgement with the generated request id
     */
    <E, P> AsyncSubmission sendAsync(String machineType, StateMachineEvent<E, P> event);
}
