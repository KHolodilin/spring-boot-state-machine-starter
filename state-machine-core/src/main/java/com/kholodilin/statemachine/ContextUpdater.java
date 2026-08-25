package com.kholodilin.statemachine;

/**
 * Pure function that updates workflow context from an event. Must not perform I/O.
 */
@FunctionalInterface
public interface ContextUpdater<E, P> {

    /**
     * @param context current workflow context (a mutable copy)
     * @param event   triggering event
     * @return context to persist; typically the same instance after {@code put}
     */
    StateMachineContext update(StateMachineContext context, StateMachineEvent<E, P> event);
}
