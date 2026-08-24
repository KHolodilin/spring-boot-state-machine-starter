package com.kholodilin.statemachine;

/**
 * Public entry point for synchronous and asynchronous event processing.
 */
public interface StateMachineService {

    <E, P> TransitionResult send(String machineType, StateMachineEvent<E, P> event);

    <E, P> AsyncSubmission sendAsync(String machineType, StateMachineEvent<E, P> event);
}
