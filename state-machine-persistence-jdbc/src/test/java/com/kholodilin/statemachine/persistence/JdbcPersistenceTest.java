package com.kholodilin.statemachine.persistence;

import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.spi.ProcessedStateMachineEvent;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JdbcPersistenceTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private JdbcTemplate jdbcTemplate;
    private JsonMaps jsonMaps;
    private JdbcStateMachineStore store;
    private JdbcStateMachineEventStore eventStore;
    private JdbcStateMachineRequestStore requestStore;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SingleConnectionDataSource(
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()),
                true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jsonMaps = new JsonMaps(JsonMapper.builder().build());
        new StateMachineSchemaManager(dataSource, SchemaMode.CREATE).apply();
        store = new JdbcStateMachineStore(jdbcTemplate, jsonMaps);
        eventStore = new JdbcStateMachineEventStore(jdbcTemplate);
        requestStore = new JdbcStateMachineRequestStore(jdbcTemplate);
        jdbcTemplate.update("TRUNCATE state_machine_instance, state_machine_event, state_machine_request");
    }

    @Test
    void createLoadAndOptimisticUpdate() {
        StateMachineInstance created = store.create(new StateMachineInstance(
                "order-saga", "order-1", "NEW", Map.of("k", "v"), 0));
        assertThat(store.find("order-saga", "order-1")).contains(created);

        StateMachineInstance next = new StateMachineInstance(
                "order-saga", "order-1", "PAYMENT_PENDING", Map.of("k", "v2"), 1);
        assertThat(store.update(next, 0)).isTrue();
        assertThat(store.update(next, 0)).isFalse();
        assertThat(store.find("order-saga", "order-1").orElseThrow().version()).isEqualTo(1);
        assertThat(store.find("order-saga", "order-1").orElseThrow().state()).isEqualTo("PAYMENT_PENDING");
    }

    @Test
    void concurrentCreateReturnsExisting() {
        StateMachineInstance first = new StateMachineInstance("order-saga", "dup", "NEW", Map.of(), 0);
        store.create(first);
        StateMachineInstance second = store.create(new StateMachineInstance(
                "order-saga", "dup", "OTHER", Map.of("x", 1), 0));
        assertThat(second.state()).isEqualTo("NEW");
    }

    @Test
    void eventInsertDuplicateAndRejected() {
        eventStore.append(new ProcessedStateMachineEvent(
                "evt-1", "order-saga", "order-1", "START", "NEW", "PAYMENT_PENDING", "success", Instant.now()));
        assertThat(eventStore.exists("evt-1")).isTrue();
        assertThat(eventStore.find("evt-1").orElseThrow().toState()).isEqualTo("PAYMENT_PENDING");

        eventStore.append(new ProcessedStateMachineEvent(
                "evt-2", "order-saga", "order-1", "START", "COMPLETED", "COMPLETED", "rejected", Instant.now()));
        assertThat(eventStore.find("evt-2").orElseThrow().result()).isEqualTo("rejected");
        assertThat(eventStore.find("evt-2").orElseThrow().fromState()).isEqualTo("COMPLETED");
    }

    @Test
    void schemaValidateAndNone() {
        new StateMachineSchemaManager(jdbcTemplate.getDataSource(), SchemaMode.VALIDATE).apply();
        new StateMachineSchemaManager(jdbcTemplate.getDataSource(), SchemaMode.NONE).apply();
        jdbcTemplate.update("DROP INDEX idx_sm_request_recovery");
        assertThatThrownBy(() -> new StateMachineSchemaManager(jdbcTemplate.getDataSource(), SchemaMode.VALIDATE).apply())
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("idx_sm_request_recovery");
    }

    @Test
    void requestAppendClaimDoneAndLeaseRecovery() {
        long id = requestStore.append(new StateMachineRequest(
                null, "evt-a", "order-saga", "order-1", "START", "{\"x\":1}", StateMachineRequest.NEW, 0,
                null, null, null, null));
        assertThat(id).isPositive();
        assertThat(requestStore.claim(id, "pod-1", Instant.now().plusSeconds(30))).isTrue();
        requestStore.markDone(id);
        assertThat(requestStore.findById(id).orElseThrow().status()).isEqualTo(StateMachineRequest.DONE);

        long recoverable = requestStore.append(new StateMachineRequest(
                null, "evt-b", "order-saga", "order-1", "START", null, StateMachineRequest.PROCESSING, 0,
                "pod-old", Instant.now().minus(1, ChronoUnit.MINUTES), null, null));
        jdbcTemplate.update(
                "UPDATE state_machine_request SET status = ?, locked_until = NOW() - INTERVAL '1 minute' WHERE id = ?",
                StateMachineRequest.PROCESSING,
                recoverable);
        var claimed = requestStore.claimRecoverable("pod-2", Instant.now().plusSeconds(30), 10);
        assertThat(claimed).extracting(StateMachineRequest::eventId).contains("evt-b");
    }

    @Test
    void payloadRoundTrip() {
        record Payload(String reservationId) {}
        String json = jsonMaps.write(new Payload("PAY-1"));
        Payload read = jsonMaps.read(json, Payload.class);
        assertThat(read.reservationId()).isEqualTo("PAY-1");
    }
}
