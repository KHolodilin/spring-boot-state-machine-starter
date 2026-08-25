package com.kholodilin.statemachine.exception;

/**
 * The fluent definition is incomplete or inconsistent (missing payload, duplicate type, etc.).
 */
public class InvalidDefinitionException extends RuntimeException {

    /**
     * @param message builder validation detail
     */
    public InvalidDefinitionException(String message) {
        super(message);
    }
}
