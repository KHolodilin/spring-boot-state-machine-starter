package com.kholodilin.statemachine;

/**
 * Snapshot passed to guards, context updaters and command factories.
 * {@link #workflowContext()} is the pre-update context for guards and the post-update
 * context for command factories.
 */
public interface TransitionContext<S extends Enum<S>, E extends Enum<E>, P> {

    String machineType();

    String machineId();

    S currentState();

    StateMachineContext workflowContext();

    StateMachineEvent<E, P> event();

    P payload();
}
