package com.kholodilin.statemachine.exception;

/**
 * No {@link com.kholodilin.statemachine.definition.StateMachineDefinition} is registered for the type.
 */
public class UnknownMachineTypeException extends RuntimeException {

    /**
     * @param machineType requested definition name
     */
    public UnknownMachineTypeException(String machineType) {
        super("Unknown state machine type: " + machineType);
    }
}
