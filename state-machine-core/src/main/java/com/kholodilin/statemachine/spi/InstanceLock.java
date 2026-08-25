package com.kholodilin.statemachine.spi;

/**
 * Serializes processing of a single machine instance. JDBC implementation uses {@code pg_advisory_xact_lock}.
 */
public interface InstanceLock {

    /**
     * Holds the lock until the current transaction ends.
     *
     * @param machineType definition name
     * @param machineId   instance identifier
     */
    void acquire(String machineType, String machineId);
}
