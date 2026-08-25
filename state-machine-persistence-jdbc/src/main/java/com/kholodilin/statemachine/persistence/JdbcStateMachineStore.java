package com.kholodilin.statemachine.persistence;

import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.spi.StateMachineStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * JDBC implementation of {@link StateMachineStore} against {@code state_machine_instance}.
 */
public final class JdbcStateMachineStore implements StateMachineStore {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMaps jsonMaps;

    /**
     * @param jdbcTemplate Spring JDBC
     * @param jsonMaps     context JSON codec
     */
    public JdbcStateMachineStore(JdbcTemplate jdbcTemplate, JsonMaps jsonMaps) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMaps = jsonMaps;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<StateMachineInstance> find(String machineType, String machineId) {
        return jdbcTemplate.query(
                """
                SELECT machine_type, machine_id, state, context::text, version
                FROM state_machine_instance
                WHERE machine_type = ? AND machine_id = ?
                """,
                mapper(),
                machineType,
                machineId).stream().findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StateMachineInstance create(StateMachineInstance instance) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO state_machine_instance (machine_type, machine_id, state, context, version)
                    VALUES (?, ?, ?, ?::jsonb, ?)
                    """,
                    instance.machineType(),
                    instance.machineId(),
                    instance.state(),
                    jsonMaps.write(instance.context()),
                    instance.version());
            return instance;
        } catch (DuplicateKeyException ex) {
            return find(instance.machineType(), instance.machineId()).orElseThrow(() -> ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean update(StateMachineInstance instance, long expectedVersion) {
        int updated = jdbcTemplate.update(
                """
                UPDATE state_machine_instance
                SET state = ?,
                    context = ?::jsonb,
                    version = version + 1,
                    updated_at = NOW()
                WHERE machine_type = ?
                  AND machine_id = ?
                  AND version = ?
                """,
                instance.state(),
                jsonMaps.write(instance.context()),
                instance.machineType(),
                instance.machineId(),
                expectedVersion);
        return updated == 1;
    }

    private RowMapper<StateMachineInstance> mapper() {
        return (ResultSet rs, int rowNum) -> mapRow(rs);
    }

    private StateMachineInstance mapRow(ResultSet rs) throws SQLException {
        return new StateMachineInstance(
                rs.getString("machine_type"),
                rs.getString("machine_id"),
                rs.getString("state"),
                jsonMaps.readMap(rs.getString("context")),
                rs.getLong("version"));
    }
}
