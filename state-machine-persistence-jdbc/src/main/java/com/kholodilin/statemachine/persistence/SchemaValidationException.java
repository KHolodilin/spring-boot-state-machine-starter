package com.kholodilin.statemachine.persistence;

public class SchemaValidationException extends RuntimeException {

    public SchemaValidationException(String message) {
        super(message);
    }
}
