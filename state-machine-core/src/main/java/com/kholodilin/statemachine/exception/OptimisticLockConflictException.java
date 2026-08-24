package com.kholodilin.statemachine.exception;

public class OptimisticLockConflictException extends RuntimeException {

    public OptimisticLockConflictException(String machineType, String machineId, long expectedVersion) {
        super("Optimistic lock conflict for " + machineType + "/" + machineId + " version " + expectedVersion);
    }
}
