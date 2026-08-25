package com.kholodilin.statemachine.persistence;

import java.sql.Timestamp;
import java.util.Optional;

import com.kholodilin.statemachine.spi.ProcessedStateMachineEvent;
import com.kholodilin.statemachine.spi.StateMachineEventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC implementation of {@link StateMachineEventStore} against {@code state_machine_event}.
 */
public final class JdbcStateMachineEventStore implements StateMachineEventStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate Spring JDBC
     */
    public JdbcStateMachineEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM state_machine_event WHERE event_id = ?", Integer.class, eventId);
        return count != null && count > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ProcessedStateMachineEvent> find(String eventId) {
        return jdbcTemplate.query("""
                SELECT event_id, machine_type, machine_id, event_type, from_state, to_state, result, created_at
                FROM state_machine_event
                WHERE event_id = ?
                """, mapper(), eventId).stream().findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void append(ProcessedStateMachineEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO state_machine_event
                    (event_id, machine_type, machine_id, event_type, from_state, to_state, result)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                event.eventId(),
                event.machineType(),
                event.machineId(),
                event.eventType(),
                event.fromState(),
                event.toState(),
                event.result());
    }

    private static RowMapper<ProcessedStateMachineEvent> mapper() {
        return (rs, rowNum) -> {
            Timestamp created = rs.getTimestamp("created_at");
            return new ProcessedStateMachineEvent(
                    rs.getString("event_id"),
                    rs.getString("machine_type"),
                    rs.getString("machine_id"),
                    rs.getString("event_type"),
                    rs.getString("from_state"),
                    rs.getString("to_state"),
                    rs.getString("result"),
                    created == null ? null : created.toInstant());
        };
    }
}
