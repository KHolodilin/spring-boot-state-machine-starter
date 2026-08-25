package com.kholodilin.statemachine;

import java.util.Objects;

/**
 * Default {@link TransitionContext} used by the engine.
 *
 * @param machineType      definition name
 * @param machineId        instance identifier
 * @param currentState     state before applying the transition
 * @param workflowContext  pre-update context for guards, post-update for command factories
 * @param event            triggering event
 */
public record DefaultTransitionContext<S extends Enum<S>, E extends Enum<E>, P>(
        String machineType,
        String machineId,
        S currentState,
        StateMachineContext workflowContext,
        StateMachineEvent<E, P> event)
        implements TransitionContext<S, E, P> {

    /**
     * Validates required fields.
     */
    public DefaultTransitionContext {
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(workflowContext, "workflowContext");
        Objects.requireNonNull(event, "event");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public P payload() {
        return event.payload();
    }
}
