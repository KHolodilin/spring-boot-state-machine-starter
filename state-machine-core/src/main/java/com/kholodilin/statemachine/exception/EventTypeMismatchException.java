package com.kholodilin.statemachine.exception;

public class EventTypeMismatchException extends RuntimeException {

    public EventTypeMismatchException(String machineType, Class<?> expected, Class<?> actual) {
        super("Event type " + actual.getName() + " does not match definition " + machineType
                + " which expects " + expected.getName());
    }
}
