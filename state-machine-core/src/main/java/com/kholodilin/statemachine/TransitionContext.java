package com.kholodilin.statemachine;

/**
 * Snapshot passed to guards, context updaters and command factories.
 * {@link #workflowContext()} is the pre-update context for guards and the post-update
 * context for command factories.
 */
public interface TransitionContext<S extends Enum<S>, E extends Enum<E>, P> {

    /**
     * @return definition name
     */
    String machineType();

    /**
     * @return instance identifier
     */
    String machineId();

    /**
     * @return state before this transition is applied
     */
    S currentState();

    /**
     * Pre-update snapshot for guards, post-update snapshot for command factories.
     *
     * @return workflow context
     */
    StateMachineContext workflowContext();

    /**
     * @return incoming event
     */
    StateMachineEvent<E, P> event();

    /**
     * @return {@link StateMachineEvent#payload()}, possibly {@code null} for {@link Void}
     */
    P payload();
}
