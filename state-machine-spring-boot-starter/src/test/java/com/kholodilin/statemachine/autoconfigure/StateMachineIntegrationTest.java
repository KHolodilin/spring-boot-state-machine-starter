package com.kholodilin.statemachine.autoconfigure;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.kholodilin.statemachine.StateMachineCommand;
import com.kholodilin.statemachine.StateMachineEvent;
import com.kholodilin.statemachine.StateMachineRegistry;
import com.kholodilin.statemachine.StateMachineService;
import com.kholodilin.statemachine.TransitionOutcome;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.async.StateMachineRecoveryWorker;
import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.spi.ProcessedStateMachineEvent;
import com.kholodilin.statemachine.spi.StateMachineEventStore;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import com.kholodilin.statemachine.spi.StateMachineStore;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        classes = StateMachineIntegrationTest.TestApp.class,
        properties = {
            "state-machine.persistence.schema.mode=create",
            "state-machine.async.workers=2",
            "state-machine.async.recovery.interval=1h",
            "state-machine.observability.tracing.enabled=true"
        })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StateMachineIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    enum OrderState {
        NEW,
        PAYMENT_PENDING,
        COMPLETED,
        CANCELLED
    }

    enum OrderEvent {
        START,
        PAYMENT_RESERVED,
        PAYMENT_REJECTED
    }

    record ReservePaymentCommand(String orderId) implements StateMachineCommand {
        @Override
        public String type() {
            return "ReservePayment";
        }

        @Override
        public Object payload() {
            return this;
        }
    }

    static final List<String> SPANS = new CopyOnWriteArrayList<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        ObservationRegistry observationRegistry() {
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
                @Override
                public boolean supportsContext(Observation.Context context) {
                    return true;
                }

                @Override
                public void onStop(Observation.Context context) {
                    SPANS.add(context.getName());
                }
            });
            return registry;
        }

        @Bean
        StateMachineDefinition<OrderState, OrderEvent> orderSaga() {
            return StateMachineDefinition.builder("order-saga", OrderState.class, OrderEvent.class)
                    .initial(OrderState.NEW)
                    .payload(OrderEvent.START, Void.class)
                    .payload(OrderEvent.PAYMENT_RESERVED, Void.class)
                    .payload(OrderEvent.PAYMENT_REJECTED, Void.class)
                    .transition()
                    .from(OrderState.NEW)
                    .event(OrderEvent.START)
                    .to(OrderState.PAYMENT_PENDING)
                    .command(ctx -> new ReservePaymentCommand(ctx.machineId()))
                    .transition()
                    .from(OrderState.PAYMENT_PENDING)
                    .event(OrderEvent.PAYMENT_RESERVED)
                    .to(OrderState.COMPLETED)
                    .transition()
                    .from(OrderState.PAYMENT_PENDING)
                    .event(OrderEvent.PAYMENT_REJECTED)
                    .to(OrderState.CANCELLED)
                    .build();
        }
    }

    @Autowired
    StateMachineService service;

    @Autowired
    StateMachineStore store;

    @Autowired
    StateMachineEventStore eventStore;

    @Autowired
    StateMachineRequestStore requestStore;

    @Autowired
    StateMachineRegistry registry;

    @Autowired
    StateMachineRecoveryWorker recoveryWorker;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void sendCreatesTransitionSpan() {
        SPANS.clear();
        service.send("order-saga", new StateMachineEvent<>("evt-span", "order-span", OrderEvent.START, null));
        assertThat(SPANS).contains("state-machine.transition");
    }

    @Test
    void expiredLeaseIsRecoveredInBatches() {
        long id = requestStore.append(new StateMachineRequest(
                null,
                "evt-expired",
                "order-saga",
                "order-expired",
                "START",
                null,
                StateMachineRequest.NEW,
                0,
                null,
                null,
                null,
                null));
        jdbcTemplate.update("""
                UPDATE state_machine_request
                SET status = ?, locked_by = 'old-pod', locked_until = NOW() - INTERVAL '1 minute'
                WHERE id = ?
                """, StateMachineRequest.PROCESSING, id);
        assertThat(recoveryWorker.recover()).isGreaterThanOrEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(requestStore.findById(id).orElseThrow().status()).isEqualTo(StateMachineRequest.DONE);
            assertThat(store.find("order-saga", "order-expired").orElseThrow().state())
                    .isEqualTo("PAYMENT_PENDING");
        });
    }

    @Test
    void sendSuccessDuplicateAndRejected() {
        TransitionResult first =
                service.send("order-saga", new StateMachineEvent<>("evt-start", "order-1", OrderEvent.START, null));
        assertThat(first.outcome()).isEqualTo(TransitionOutcome.SUCCESS);
        assertThat(((TransitionResult.Success) first).toState()).isEqualTo("PAYMENT_PENDING");
        assertThat(((TransitionResult.Success) first).commands()).hasSize(1);
        assertThat(store.find("order-saga", "order-1").orElseThrow().state()).isEqualTo("PAYMENT_PENDING");

        TransitionResult duplicate =
                service.send("order-saga", new StateMachineEvent<>("evt-start", "order-1", OrderEvent.START, null));
        assertThat(duplicate.outcome()).isEqualTo(TransitionOutcome.DUPLICATE);

        TransitionResult reserved = service.send(
                "order-saga", new StateMachineEvent<>("evt-pay", "order-1", OrderEvent.PAYMENT_RESERVED, null));
        assertThat(reserved.outcome()).isEqualTo(TransitionOutcome.SUCCESS);

        TransitionResult rejected =
                service.send("order-saga", new StateMachineEvent<>("evt-late", "order-1", OrderEvent.START, null));
        assertThat(rejected.outcome()).isEqualTo(TransitionOutcome.REJECTED);
        ProcessedStateMachineEvent stored = eventStore.find("evt-late").orElseThrow();
        assertThat(stored.result()).isEqualTo("rejected");
        assertThat(stored.fromState()).isEqualTo(stored.toState());

        TransitionResult rejectedAgain =
                service.send("order-saga", new StateMachineEvent<>("evt-late", "order-1", OrderEvent.START, null));
        assertThat(rejectedAgain.outcome()).isEqualTo(TransitionOutcome.DUPLICATE);
    }

    @Test
    void sendAsyncIsProcessedByWorker() {
        service.sendAsync("order-saga", new StateMachineEvent<>("evt-async", "order-async", OrderEvent.START, null));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(store.find("order-saga", "order-async")).isPresent();
            assertThat(store.find("order-saga", "order-async").orElseThrow().state())
                    .isEqualTo("PAYMENT_PENDING");
            assertThat(requestStore.findByEventId("evt-async").orElseThrow().status())
                    .isEqualTo(StateMachineRequest.DONE);
        });
    }

    @Test
    void recoveryRequeuesLostMemoryItem() {
        var submission = service.sendAsync(
                "order-saga", new StateMachineEvent<>("evt-rec", "order-rec", OrderEvent.START, null));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(requestStore
                        .findById(submission.requestId())
                        .orElseThrow()
                        .status())
                .isEqualTo(StateMachineRequest.DONE));
        assertThat(registry.find("order-saga")).isPresent();
    }
}
