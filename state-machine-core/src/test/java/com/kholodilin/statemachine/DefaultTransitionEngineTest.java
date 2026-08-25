package com.kholodilin.statemachine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.engine.DefaultTransitionEngine;
import com.kholodilin.statemachine.engine.TransitionEngine;
import com.kholodilin.statemachine.exception.AmbiguousTransitionException;
import com.kholodilin.statemachine.exception.InvalidDefinitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTransitionEngineTest {

    private final TransitionEngine engine = new DefaultTransitionEngine();

    enum OrderState {
        NEW,
        PAYMENT_PENDING,
        INVENTORY_PENDING,
        COMPLETED,
        CANCELLED
    }

    enum OrderEvent {
        START,
        PAYMENT_RESERVED,
        PAYMENT_REJECTED,
        INVENTORY_RESERVED
    }

    record PaymentReservedPayload(String reservationId, BigDecimal amount) {}

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

    record ReserveInventoryCommand(String orderId, String paymentReservationId) implements StateMachineCommand {
        @Override
        public String type() {
            return "ReserveInventory";
        }

        @Override
        public Object payload() {
            return this;
        }
    }

    @Test
    void requiresPayloadRegistrationBeforeTransition() {
        assertThatThrownBy(() -> StateMachineDefinition.builder("order-saga", OrderState.class, OrderEvent.class)
                        .initial(OrderState.NEW)
                        .transition()
                        .from(OrderState.NEW)
                        .event(OrderEvent.START))
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void duplicatePayloadRegistrationFails() {
        assertThatThrownBy(() -> StateMachineDefinition.builder("order-saga", OrderState.class, OrderEvent.class)
                        .payload(OrderEvent.START, Void.class)
                        .payload(OrderEvent.START, Void.class))
                .isInstanceOf(InvalidDefinitionException.class);
    }

    @Test
    void duplicateMachineTypeFails() {
        StateMachineDefinition<OrderState, OrderEvent> definition = emptyStartDefinition();
        assertThatThrownBy(() -> new StateMachineRegistry.InMemory(List.of(definition, definition)))
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("Duplicate machineType");
    }

    @Test
    void successfulTransitionWithContextAndCommand() {
        StateMachineDefinition<OrderState, OrderEvent> definition = StateMachineDefinition.builder(
                        "order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .payload(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(OrderState.PAYMENT_PENDING)
                .command(ctx -> new ReservePaymentCommand(ctx.machineId()))
                .transition()
                .from(OrderState.PAYMENT_PENDING)
                .event(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
                .to(OrderState.INVENTORY_PENDING)
                .updateContext((ctx, event) ->
                        ctx.put("paymentReservationId", event.payload().reservationId()))
                .command(ctx -> new ReserveInventoryCommand(
                        ctx.machineId(),
                        ctx.workflowContext().getString("paymentReservationId").orElseThrow()))
                .build();

        StateMachineInstance instance =
                new StateMachineInstance("order-saga", "order-1", OrderState.PAYMENT_PENDING.name(), Map.of(), 1);
        StateMachineEvent<OrderEvent, PaymentReservedPayload> event = new StateMachineEvent<>(
                "evt-1", "order-1", OrderEvent.PAYMENT_RESERVED, new PaymentReservedPayload("PAY-9", BigDecimal.TEN));

        TransitionResult result = engine.transition(definition, instance, event);

        assertThat(result).isInstanceOf(TransitionResult.Success.class);
        TransitionResult.Success success = (TransitionResult.Success) result;
        assertThat(success.fromState()).isEqualTo("PAYMENT_PENDING");
        assertThat(success.toState()).isEqualTo("INVENTORY_PENDING");
        assertThat(success.version()).isEqualTo(2);
        assertThat(success.context()).containsEntry("paymentReservationId", "PAY-9");
        assertThat(success.commands()).hasSize(1);
        ReserveInventoryCommand command =
                (ReserveInventoryCommand) success.commands().getFirst();
        assertThat(command.paymentReservationId()).isEqualTo("PAY-9");
    }

    @Test
    void zeroCommands() {
        StateMachineDefinition<OrderState, OrderEvent> definition = StateMachineDefinition.builder(
                        "order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.PAYMENT_REJECTED, Void.class)
                .transition()
                .from(OrderState.PAYMENT_PENDING)
                .event(OrderEvent.PAYMENT_REJECTED)
                .to(OrderState.CANCELLED)
                .build();
        StateMachineInstance instance =
                new StateMachineInstance("order-saga", "order-1", "PAYMENT_PENDING", Map.of(), 0);
        TransitionResult result = engine.transition(
                definition, instance, new StateMachineEvent<>("evt-r", "order-1", OrderEvent.PAYMENT_REJECTED, null));
        TransitionResult.Success success = (TransitionResult.Success) result;
        assertThat(success.commands()).isEmpty();
        assertThat(success.toState()).isEqualTo("CANCELLED");
    }

    @Test
    void multipleCommands() {
        StateMachineDefinition<OrderState, OrderEvent> definition = StateMachineDefinition.builder(
                        "order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(OrderState.PAYMENT_PENDING)
                .command(ctx -> new ReservePaymentCommand(ctx.machineId() + "-a"))
                .command(ctx -> new ReservePaymentCommand(ctx.machineId() + "-b"))
                .build();
        TransitionResult.Success success = (TransitionResult.Success) engine.transition(
                definition,
                new StateMachineInstance("order-saga", "order-1", "NEW", Map.of(), 0),
                new StateMachineEvent<>("evt-s", "order-1", OrderEvent.START, null));
        assertThat(success.commands()).hasSize(2);
    }

    @Test
    void guardTrueAndFalse() {
        StateMachineDefinition<OrderState, OrderEvent> definition = StateMachineDefinition.builder(
                        "order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
                .transition()
                .from(OrderState.PAYMENT_PENDING)
                .event(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
                .when(ctx -> ctx.payload().amount().signum() > 0)
                .to(OrderState.INVENTORY_PENDING)
                .build();
        StateMachineInstance instance =
                new StateMachineInstance("order-saga", "order-1", "PAYMENT_PENDING", Map.of(), 0);

        TransitionResult success = engine.transition(
                definition,
                instance,
                new StateMachineEvent<>(
                        "ok",
                        "order-1",
                        OrderEvent.PAYMENT_RESERVED,
                        new PaymentReservedPayload("PAY", BigDecimal.ONE)));
        assertThat(success.outcome()).isEqualTo(TransitionOutcome.SUCCESS);

        TransitionResult rejected = engine.transition(
                definition,
                instance,
                new StateMachineEvent<>(
                        "bad",
                        "order-1",
                        OrderEvent.PAYMENT_RESERVED,
                        new PaymentReservedPayload("PAY", BigDecimal.ZERO)));
        assertThat(rejected).isInstanceOf(TransitionResult.Rejected.class);
        assertThat(((TransitionResult.Rejected) rejected).reason()).isEqualTo(RejectedReason.GUARD_NOT_MATCHED);
    }

    @Test
    void ambiguityThrows() {
        StateMachineDefinition<OrderState, OrderEvent> definition = StateMachineDefinition.builder(
                        "order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .when(ctx -> true)
                .to(OrderState.PAYMENT_PENDING)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .when(ctx -> true)
                .to(OrderState.CANCELLED)
                .build();
        assertThatThrownBy(() -> engine.transition(
                        definition,
                        new StateMachineInstance("order-saga", "order-1", "NEW", Map.of(), 0),
                        new StateMachineEvent<>("evt", "order-1", OrderEvent.START, null)))
                .isInstanceOf(AmbiguousTransitionException.class);
    }

    @Test
    void noTransitionIsRejected() {
        StateMachineDefinition<OrderState, OrderEvent> definition = emptyStartDefinition();
        TransitionResult result = engine.transition(
                definition,
                new StateMachineInstance("order-saga", "order-1", "COMPLETED", Map.of(), 3),
                new StateMachineEvent<>("evt", "order-1", OrderEvent.START, null));
        assertThat(result).isInstanceOf(TransitionResult.Rejected.class);
        assertThat(((TransitionResult.Rejected) result).reason()).isEqualTo(RejectedReason.NO_TRANSITION);
        assertThat(((TransitionResult.Rejected) result).state()).isEqualTo("COMPLETED");
    }

    @Test
    void contextUnchangedWithoutUpdater() {
        StateMachineDefinition<OrderState, OrderEvent> definition = emptyStartDefinition();
        TransitionResult.Success success = (TransitionResult.Success) engine.transition(
                definition,
                new StateMachineInstance("order-saga", "order-1", "NEW", Map.of("keep", "yes"), 0),
                new StateMachineEvent<>("evt", "order-1", OrderEvent.START, null));
        assertThat(success.context()).containsEntry("keep", "yes");
    }

    @Test
    void getLongAcceptsInteger() {
        StateMachineContext context = MapStateMachineContext.empty().put("n", 7);
        assertThat(context.getLong("n")).contains(7L);
        assertThat(context.getInt("n")).contains(7);
    }

    private static StateMachineDefinition<OrderState, OrderEvent> emptyStartDefinition() {
        return StateMachineDefinition.builder("order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(OrderState.PAYMENT_PENDING)
                .build();
    }
}
