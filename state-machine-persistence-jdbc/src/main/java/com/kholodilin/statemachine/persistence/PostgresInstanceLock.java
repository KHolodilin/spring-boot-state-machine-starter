package com.kholodilin.statemachine.persistence;

import com.kholodilin.statemachine.spi.InstanceLock;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Transaction-scoped lock via {@code pg_advisory_xact_lock(hashtext(type), hashtext(id))}.
 */
public final class PostgresInstanceLock implements InstanceLock {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate must share the same transaction as {@code send()}
     */
    public PostgresInstanceLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acquire(String machineType, String machineId) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))", rs -> null, machineType, machineId);
    }
}
