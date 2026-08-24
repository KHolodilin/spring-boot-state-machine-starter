package com.kholodilin.statemachine.exception;

public class AmbiguousTransitionException extends RuntimeException {

    public AmbiguousTransitionException(String message) {
        super(message);
    }
}
