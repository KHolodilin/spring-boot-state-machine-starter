package com.kholodilin.statemachine.exception;

public class UnknownMachineTypeException extends RuntimeException {

    public UnknownMachineTypeException(String machineType) {
        super("Unknown state machine type: " + machineType);
    }
}
