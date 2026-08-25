package com.kholodilin.statemachine.exception;

/**
 * {@code UPDATE ... WHERE version = ?} updated zero rows.
 */
public class OptimisticLockConflictException extends RuntimeException {

    /**
     * @param machineType     definition name
     * @param machineId       instance identifier
     * @param expectedVersion version that was believed to be current
     */
    public OptimisticLockConflictException(String machineType, String machineId, long expectedVersion) {
        super("Optimistic lock conflict for " + machineType + "/" + machineId + " version " + expectedVersion);
    }
}
