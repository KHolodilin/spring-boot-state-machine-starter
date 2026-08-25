package com.kholodilin.statemachine.exception;

/**
 * The event enum type does not match the definition's event class.
 */
public class EventTypeMismatchException extends RuntimeException {

    /**
     * @param machineType definition name
     * @param expected    definition event class
     * @param actual      runtime class of {@code event.type()}
     */
    public EventTypeMismatchException(String machineType, Class<?> expected, Class<?> actual) {
        super("Event type " + actual.getName() + " does not match definition " + machineType + " which expects "
                + expected.getName());
    }
}
