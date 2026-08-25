package com.kholodilin.statemachine.exception;

/**
 * JSON payload or context could not be serialized or deserialized.
 */
public class PayloadDeserializationException extends RuntimeException {

    /**
     * @param message human-readable detail
     * @param cause   Jackson or mapping failure
     */
    public PayloadDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
