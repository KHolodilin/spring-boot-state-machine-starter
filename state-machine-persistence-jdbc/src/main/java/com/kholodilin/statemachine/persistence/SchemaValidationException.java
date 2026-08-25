package com.kholodilin.statemachine.persistence;

/**
 * Schema {@code validate} found missing tables, columns or constraints.
 */
public class SchemaValidationException extends RuntimeException {

    /**
     * @param message aggregated validation errors
     */
    public SchemaValidationException(String message) {
        super(message);
    }
}
