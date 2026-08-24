package com.kholodilin.statemachine;

/**
 * Pure factory that builds a command from the transition snapshot. Must not perform I/O.
 */
@FunctionalInterface
public interface StateMachineCommandFactory<S extends Enum<S>, E extends Enum<E>, P> {

    StateMachineCommand create(TransitionContext<S, E, P> ctx);
}
