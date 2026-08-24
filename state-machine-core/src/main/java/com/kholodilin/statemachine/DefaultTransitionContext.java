package com.kholodilin.statemachine;

import java.util.Objects;

public record DefaultTransitionContext<S extends Enum<S>, E extends Enum<E>, P>(
        String machineType,
        String machineId,
        S currentState,
        StateMachineContext workflowContext,
        StateMachineEvent<E, P> event
) implements TransitionContext<S, E, P> {

    public DefaultTransitionContext {
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(currentState, "currentState");
        Objects.requireNonNull(workflowContext, "workflowContext");
        Objects.requireNonNull(event, "event");
    }

    @Override
    public P payload() {
        return event.payload();
    }
}
