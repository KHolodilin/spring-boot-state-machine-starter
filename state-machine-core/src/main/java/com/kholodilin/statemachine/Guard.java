package com.kholodilin.statemachine;

/**
 * Pure predicate deciding whether a transition is allowed. Must not perform I/O.
 */
@FunctionalInterface
public interface Guard<S extends Enum<S>, E extends Enum<E>, P> {

    boolean test(TransitionContext<S, E, P> context);
}
