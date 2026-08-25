package com.kholodilin.statemachine;

import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.definition.Transition;
import com.kholodilin.statemachine.engine.DefaultTransitionEngine;
import com.kholodilin.statemachine.exception.EventTypeMismatchException;
import com.kholodilin.statemachine.exception.InvalidDefinitionException;
import com.kholodilin.statemachine.exception.OptimisticLockConflictException;
import com.kholodilin.statemachine.exception.PayloadDeserializationException;
import com.kholodilin.statemachine.exception.UnknownMachineTypeException;
import com.kholodilin.statemachine.spi.NoOpCommandPublisher;
import com.kholodilin.statemachine.spi.ProcessedStateMachineEvent;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreApiTest {

    enum OrderState { NEW, PENDING, DONE }

    enum OrderEvent { START, FINISH }

    enum OtherEvent { X }

    @Test
    void contextAccessorsAndMutations() {
        StateMachineContext context = new MapStateMachineContext(null)
                .put("id", "abc")
                .put("n", 3L)
                .put("flag", true)
                .put("flagText", "true")
                .put("other", "no");

        assertThat(context.getString("id")).contains("abc");
        assertThat(context.getString("missing")).isEmpty();
        assertThat(context.getLong("n")).contains(3L);
        assertThat(context.getInt("n")).contains(3);
        assertThat(context.getLong("id")).isEmpty();
        assertThat(context.getBoolean("flag")).contains(true);
        assertThat(context.getBoolean("flagText")).contains(true);
        assertThat(context.getBoolean("other")).contains(false);
        assertThat(context.getBoolean("n")).isEmpty();
        assertThat(context.getInt("missing")).isEmpty();
        assertThat(context.get("id", String.class)).contains("abc");
        assertThat(context.get("id", Integer.class)).isEmpty();
        context.remove("id");
        assertThat(context.asMap()).doesNotContainKey("id");
        assertThat(MapStateMachineContext.empty().asMap()).isEmpty();
        assertThat(MapStateMachineContext.copyOf(Map.of("k", "v")).getString("k")).contains("v");
        assertThatThrownBy(() -> context.put(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> context.get("id", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void eventAndInstanceValidation() {
        assertThatThrownBy(() -> new StateMachineEvent<>(" ", "m", OrderEvent.START, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StateMachineEvent<>("e", " ", OrderEvent.START, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StateMachineEvent<>(null, "m", OrderEvent.START, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StateMachineEvent<>("e", "m", null, null))
                .isInstanceOf(NullPointerException.class);

        StateMachineInstance instance = new StateMachineInstance("order", "1", "NEW", null, 0);
        assertThat(instance.context()).isEmpty();
        assertThat(instance.workflowContext().asMap()).isEmpty();
    }

    @Test
    void transitionResultRecords() {
        TransitionResult.Duplicate duplicate = new TransitionResult.Duplicate(
                "order", "1", "evt", "START", "NEW", "PENDING");
        assertThat(duplicate.outcome()).isEqualTo(TransitionOutcome.DUPLICATE);

        TransitionResult.Success success = new TransitionResult.Success(
                "order", "1", "evt", "START", "NEW", "PENDING", 1, null, null);
        assertThat(success.commands()).isEmpty();
        assertThat(success.context()).isEmpty();
        assertThat(success.outcome()).isEqualTo(TransitionOutcome.SUCCESS);

        AsyncSubmission submission = new AsyncSubmission("evt", "order", "1", 9L);
        assertThat(submission.requestId()).isEqualTo(9L);

        TransitionResult.Rejected rejected = new TransitionResult.Rejected(
                "order", "1", "evt", "START", "NEW", RejectedReason.NO_TRANSITION);
        assertThat(rejected.outcome()).isEqualTo(TransitionOutcome.REJECTED);
    }

    @Test
    void registryFindGetAndAll() {
        StateMachineDefinition<OrderState, OrderEvent> definition = startDefinition();
        StateMachineRegistry.InMemory registry = new StateMachineRegistry.InMemory(List.of(definition));

        assertThat(registry.getRequired("order-saga")).isSameAs(definition);
        assertThat(registry.find("order-saga")).contains(definition);
        assertThat(registry.find("missing")).isEmpty();
        assertThat(registry.all()).containsExactly(definition);
        assertThatThrownBy(() -> registry.getRequired("missing"))
                .isInstanceOf(UnknownMachineTypeException.class)
                .hasMessageContaining("missing");
        assertThat(new StateMachineRegistry.InMemory(List.of()).all()).isEmpty();
    }

    @Test
    void definitionAccessorsAndPayloadLookup() {
        StateMachineDefinition<OrderState, OrderEvent> definition = startDefinition();
        assertThat(definition.machineType()).isEqualTo("order-saga");
        assertThat(definition.stateType()).isEqualTo(OrderState.class);
        assertThat(definition.eventType()).isEqualTo(OrderEvent.class);
        assertThat(definition.initialState()).isEqualTo(OrderState.NEW);
        assertThat(definition.payloadType(OrderEvent.START)).isEqualTo(Void.class);
        assertThat(definition.findPayloadType("START")).contains(Void.class);
        assertThat(definition.findPayloadType("UNKNOWN")).isEmpty();
        assertThat(definition.eventFromName("START")).isEqualTo(OrderEvent.START);
        assertThat(definition.stateFromName("NEW")).isEqualTo(OrderState.NEW);
        assertThat(definition.transitions()).hasSize(1);
        assertThat(definition.transitions().getFirst().hasGuard()).isFalse();
        assertThat(definition.matching(OrderState.NEW, OrderEvent.START)).hasSize(1);
        assertThat(definition.matching(OrderState.DONE, OrderEvent.START)).isEmpty();
        assertThatThrownBy(() -> definition.payloadType(OrderEvent.FINISH))
                .isInstanceOf(InvalidDefinitionException.class);
        assertThatThrownBy(() -> definition.eventFromName("NOPE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> definition.stateFromName("NOPE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderValidation() {
        assertThatThrownBy(() -> StateMachineDefinition.builder(" ", OrderState.class, OrderEvent.class))
                .isInstanceOf(InvalidDefinitionException.class);
        assertThatThrownBy(() -> StateMachineDefinition.builder(null, OrderState.class, OrderEvent.class))
                .isInstanceOf(InvalidDefinitionException.class);
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(OrderEvent.START, Void.class)
                .build())
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("initial");
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .build())
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining(".to(");
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START, String.class))
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .initial(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(null, Void.class))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .when(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .updateContext(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .command(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateMachineDefinition.builder("order-saga", null, OrderEvent.class))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void payloadRegistrationAfterTransitionAndGuardedTransition() {
        StateMachineDefinition<OrderState, OrderEvent> definition = StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .when(ctx -> true)
                .to(OrderState.PENDING)
                .payload(OrderEvent.FINISH, Void.class)
                .transition()
                .from(OrderState.PENDING)
                .event(OrderEvent.FINISH)
                .to(OrderState.DONE)
                .build();
        assertThat(definition.payloadType(OrderEvent.FINISH)).isEqualTo(Void.class);
        assertThat(definition.transitions().getFirst().hasGuard()).isTrue();
    }

    @Test
    void engineRejectsMismatchedEventType() {
        StateMachineDefinition<OrderState, OrderEvent> definition = startDefinition();
        assertThatThrownBy(() -> new DefaultTransitionEngine().transition(
                definition,
                new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0),
                new StateMachineEvent<>("e", "1", OtherEvent.X, null)))
                .isInstanceOf(EventTypeMismatchException.class);
    }

    @Test
    void remainingExceptionsAndSpi() {
        assertThat(new OptimisticLockConflictException("t", "id", 3).getMessage()).contains("version 3");
        assertThat(new PayloadDeserializationException("bad", new IllegalStateException()).getCause())
                .isInstanceOf(IllegalStateException.class);

        ProcessedStateMachineEvent processed = new ProcessedStateMachineEvent(
                "e", "t", "id", "START", "NEW", "PENDING", "success", Instant.now());
        assertThat(processed.result()).isEqualTo("success");

        StateMachineRequest request = new StateMachineRequest(
                1L, "e", "t", "id", "START", "{}", StateMachineRequest.NEW, 0, null, null, null, null);
        assertThat(request.status()).isEqualTo(StateMachineRequest.NEW);

        new NoOpCommandPublisher().publish(
                new StateMachineInstance("t", "id", "NEW", Map.of(), 0),
                List.of());

        Transition<OrderState, OrderEvent, Void> withoutGuard = new Transition<>(
                OrderState.NEW, OrderEvent.START, Void.class, null, OrderState.PENDING, null, null);
        assertThat(withoutGuard.hasGuard()).isFalse();
        assertThat(withoutGuard.commandFactories()).isEmpty();

        Transition<OrderState, OrderEvent, Void> withGuard = new Transition<>(
                OrderState.NEW, OrderEvent.START, Void.class, ctx -> true, OrderState.PENDING, null, List.of());
        assertThat(withGuard.hasGuard()).isTrue();

        DefaultTransitionContext<OrderState, OrderEvent, String> transitionContext = new DefaultTransitionContext<>(
                "order-saga",
                "1",
                OrderState.NEW,
                MapStateMachineContext.empty(),
                new StateMachineEvent<>("e", "1", OrderEvent.START, "body"));
        assertThat(transitionContext.payload()).isEqualTo("body");
        assertThatThrownBy(() -> new DefaultTransitionContext<>(
                "order-saga", "1", OrderState.NEW, null, new StateMachineEvent<>("e", "1", OrderEvent.START, null)))
                .isInstanceOf(NullPointerException.class);
    }

    private static StateMachineDefinition<OrderState, OrderEvent> startDefinition() {
        return StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(OrderState.PENDING)
                .build();
    }
}
