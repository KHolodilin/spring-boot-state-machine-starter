package com.kholodilin.statemachine.persistence;

import com.kholodilin.statemachine.spi.InstanceLock;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresInstanceLock implements InstanceLock {

    private final JdbcTemplate jdbcTemplate;

    public PostgresInstanceLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void acquire(String machineType, String machineId) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                rs -> null,
                machineType,
                machineId);
    }
}
