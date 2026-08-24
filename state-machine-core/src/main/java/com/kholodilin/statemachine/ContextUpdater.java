package com.kholodilin.statemachine;

/**
 * Pure function that updates workflow context from an event. Must not perform I/O.
 */
@FunctionalInterface
public interface ContextUpdater<E, P> {

    StateMachineContext update(StateMachineContext context, StateMachineEvent<E, P> event);
}
