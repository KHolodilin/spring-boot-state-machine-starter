package com.kholodilin.statemachine.exception;

/**
 * More than one transition matched the same state, event and passing guards.
 */
public class AmbiguousTransitionException extends RuntimeException {

    /**
     * @param message includes machine type, state and event
     */
    public AmbiguousTransitionException(String message) {
        super(message);
    }
}
