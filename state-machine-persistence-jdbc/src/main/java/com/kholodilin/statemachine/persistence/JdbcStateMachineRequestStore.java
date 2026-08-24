package com.kholodilin.statemachine.persistence;

import com.kholodilin.statemachine.spi.StateMachineRequest;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class JdbcStateMachineRequestStore implements StateMachineRequestStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStateMachineRequestStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long append(StateMachineRequest request) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO state_machine_request
                    (event_id, machine_type, machine_id, event_type, payload, status, retry_count)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                RETURNING id
                """,
                Long.class,
                request.eventId(),
                request.machineType(),
                request.machineId(),
                request.eventType(),
                request.payloadJson(),
                request.status(),
                request.retryCount());
        if (id == null) {
            throw new IllegalStateException("Failed to obtain generated request id");
        }
        return id;
    }

    @Override
    public Optional<StateMachineRequest> findById(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM state_machine_request WHERE id = ?",
                mapper(),
                id).stream().findFirst();
    }

    @Override
    public Optional<StateMachineRequest> findByEventId(String eventId) {
        return jdbcTemplate.query(
                "SELECT * FROM state_machine_request WHERE event_id = ?",
                mapper(),
                eventId).stream().findFirst();
    }

    @Override
    public List<StateMachineRequest> claimRecoverable(String lockedBy, Instant lockedUntil, int batchSize) {
        return jdbcTemplate.query(
                """
                WITH picked AS (
                    SELECT id
                    FROM state_machine_request
                    WHERE status IN (?, ?, ?)
                      AND (status <> ? OR locked_until IS NULL OR locked_until < NOW())
                    ORDER BY id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE state_machine_request r
                SET status = ?,
                    locked_by = ?,
                    locked_until = ?
                FROM picked
                WHERE r.id = picked.id
                RETURNING r.*
                """,
                mapper(),
                StateMachineRequest.NEW,
                StateMachineRequest.FAILED,
                StateMachineRequest.PROCESSING,
                StateMachineRequest.PROCESSING,
                batchSize,
                StateMachineRequest.PROCESSING,
                lockedBy,
                Timestamp.from(lockedUntil));
    }

    @Override
    public boolean claim(long id, String lockedBy, Instant lockedUntil) {
        int updated = jdbcTemplate.update(
                """
                UPDATE state_machine_request
                SET status = ?,
                    locked_by = ?,
                    locked_until = ?
                WHERE id = ?
                  AND status IN (?, ?, ?)
                  AND (status <> ? OR locked_until IS NULL OR locked_until < NOW())
                """,
                StateMachineRequest.PROCESSING,
                lockedBy,
                Timestamp.from(lockedUntil),
                id,
                StateMachineRequest.NEW,
                StateMachineRequest.FAILED,
                StateMachineRequest.PROCESSING,
                StateMachineRequest.PROCESSING);
        return updated == 1;
    }

    @Override
    public void markDone(long id) {
        jdbcTemplate.update(
                """
                UPDATE state_machine_request
                SET status = ?, processed_at = NOW(), locked_by = NULL, locked_until = NULL
                WHERE id = ?
                """,
                StateMachineRequest.DONE,
                id);
    }

    @Override
    public void markFailed(long id, int retryCount) {
        jdbcTemplate.update(
                """
                UPDATE state_machine_request
                SET status = ?, retry_count = ?, locked_by = NULL, locked_until = NULL
                WHERE id = ?
                """,
                StateMachineRequest.FAILED,
                retryCount,
                id);
    }

    @Override
    public void markDead(long id) {
        jdbcTemplate.update(
                """
                UPDATE state_machine_request
                SET status = ?, processed_at = NOW(), locked_by = NULL, locked_until = NULL
                WHERE id = ?
                """,
                StateMachineRequest.DEAD,
                id);
    }

    @Override
    public Instant oldestPendingCreatedAt() {
        return jdbcTemplate.query(
                """
                SELECT MIN(created_at) AS oldest
                FROM state_machine_request
                WHERE status < ?
                """,
                rs -> rs.next() ? optionalInstant(rs.getTimestamp("oldest")) : null,
                StateMachineRequest.DONE);
    }

    private static Instant optionalInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static RowMapper<StateMachineRequest> mapper() {
        return (rs, rowNum) -> new StateMachineRequest(
                rs.getLong("id"),
                rs.getString("event_id"),
                rs.getString("machine_type"),
                rs.getString("machine_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getInt("status"),
                rs.getInt("retry_count"),
                rs.getString("locked_by"),
                optionalInstant(rs.getTimestamp("locked_until")),
                optionalInstant(rs.getTimestamp("created_at")),
                optionalInstant(rs.getTimestamp("processed_at")));
    }
}
