package com.kholodilin.statemachine.persistence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Applies {@code create}, {@code validate} or {@code none} against PostgreSQL at startup.
 */
public final class StateMachineSchemaManager {

    private static final String SCHEMA_RESOURCE = "state-machine-schema.sql";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final SchemaMode mode;

    /**
     * @param dataSource target database
     * @param mode       from {@code state-machine.persistence.schema.mode}
     */
    public StateMachineSchemaManager(DataSource dataSource, SchemaMode mode) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.mode = mode;
    }

    /**
     * Runs create/validate according to {@link SchemaMode}.
     */
    public void apply() {
        switch (mode) {
            case CREATE -> create();
            case VALIDATE -> validate();
            case NONE -> {
                // application manages schema (Flyway/Liquibase)
            }
        }
    }

    private void create() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(SCHEMA_RESOURCE));
        populator.setSeparator(";");
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private void validate() {
        List<String> errors = new ArrayList<>();
        requireTable("state_machine_instance", errors);
        requireTable("state_machine_event", errors);
        requireTable("state_machine_request", errors);
        requireColumns(
                "state_machine_instance",
                Set.of("machine_type", "machine_id", "state", "context", "version", "created_at", "updated_at"),
                errors);
        requireColumns(
                "state_machine_event",
                Set.of(
                        "event_id",
                        "machine_type",
                        "machine_id",
                        "event_type",
                        "from_state",
                        "to_state",
                        "result",
                        "created_at"),
                errors);
        requireColumns(
                "state_machine_request",
                Set.of(
                        "id",
                        "event_id",
                        "machine_type",
                        "machine_id",
                        "event_type",
                        "payload",
                        "status",
                        "retry_count",
                        "locked_by",
                        "locked_until",
                        "created_at",
                        "processed_at"),
                errors);
        requirePrimaryKey("state_machine_instance", List.of("machine_type", "machine_id"), errors);
        requireUnique("state_machine_event", "event_id", errors);
        requireUnique("state_machine_request", "event_id", errors);
        requireIndex("idx_sm_request_recovery", errors);
        if (!errors.isEmpty()) {
            throw new SchemaValidationException("State machine schema is incompatible: " + String.join("; ", errors));
        }
    }

    private void requireTable(String table, List<String> errors) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = ?
                """, Integer.class, table);
        if (count == null || count == 0) {
            errors.add("missing table " + table);
        }
    }

    private void requireColumns(String table, Set<String> required, List<String> errors) {
        List<String> columns =
                jdbcTemplate.query("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                """, (rs, rowNum) -> rs.getString(1).toLowerCase(Locale.ROOT), table);
        Set<String> present = new HashSet<>(columns);
        for (String column : required) {
            if (!present.contains(column)) {
                errors.add("table " + table + " missing column " + column);
            }
        }
    }

    private void requirePrimaryKey(String table, List<String> columns, List<String> errors) {
        List<String> pk =
                jdbcTemplate.query("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'PRIMARY KEY'
                ORDER BY kcu.ordinal_position
                """, (rs, rowNum) -> rs.getString(1).toLowerCase(Locale.ROOT), table);
        if (!pk.equals(columns)) {
            errors.add("table " + table + " primary key expected " + columns + " but was " + pk);
        }
    }

    private void requireUnique(String table, String column, List<String> errors) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ?
                  AND kcu.column_name = ?
                  AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
                """, Integer.class, table, column);
        if (count == null || count == 0) {
            errors.add("table " + table + " missing unique/pk on " + column);
        }
    }

    private void requireIndex(String indexName, List<String> errors) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = current_schema() AND indexname = ?
                """, Integer.class, indexName);
        if (count == null || count == 0) {
            errors.add("missing index " + indexName);
        }
    }
}
