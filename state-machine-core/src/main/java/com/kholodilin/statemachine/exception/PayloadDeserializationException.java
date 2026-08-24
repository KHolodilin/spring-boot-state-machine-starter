package com.kholodilin.statemachine.exception;

public class PayloadDeserializationException extends RuntimeException {

    public PayloadDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
