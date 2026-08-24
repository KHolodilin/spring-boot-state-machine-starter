package com.kholodilin.statemachine.spi;

/**
 * Serializes processing of a single machine instance. JDBC implementation uses pg_advisory_xact_lock.
 */
public interface InstanceLock {

    void acquire(String machineType, String machineId);
}
