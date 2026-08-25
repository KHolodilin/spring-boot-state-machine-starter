package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.definition.StateMachineDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process order saga used by the demo module.
 */
@Configuration
public class OrderSagaConfiguration {

    /**
     * Workflow states of the sample order saga.
     */
    public enum OrderState {
        NEW,
        PAYMENT_PENDING,
        INVENTORY_PENDING,
        PAYMENT_COMPENSATION_PENDING,
        COMPLETED,
        CANCELLED
    }

    /**
     * Events of the sample order saga.
     */
    public enum OrderEvent {
        START,
        PAYMENT_RESERVED,
        PAYMENT_REJECTED,
        INVENTORY_RESERVED,
        INVENTORY_REJECTED,
        PAYMENT_RELEASED
    }

    /**
     * @return definition named {@code order-saga}
     */
    @Bean
    StateMachineDefinition<OrderState, OrderEvent> orderSaga() {
        return StateMachineDefinition
                .builder("order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .payload(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
                .payload(OrderEvent.PAYMENT_REJECTED, Void.class)
                .payload(OrderEvent.INVENTORY_RESERVED, Void.class)
                .payload(OrderEvent.INVENTORY_REJECTED, Void.class)
                .payload(OrderEvent.PAYMENT_RELEASED, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(OrderState.PAYMENT_PENDING)
                .command(ctx -> new ReservePaymentCommand(ctx.machineId()))
                .transition()
                .from(OrderState.PAYMENT_PENDING)
                .event(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
                .to(OrderState.INVENTORY_PENDING)
                .updateContext((ctx, event) -> ctx.put("paymentReservationId", event.payload().reservationId()))
                .command(ctx -> new ReserveInventoryCommand(
                        ctx.machineId(),
                        ctx.workflowContext().getString("paymentReservationId").orElseThrow()))
                .transition()
                .from(OrderState.PAYMENT_PENDING)
                .event(OrderEvent.PAYMENT_REJECTED)
                .to(OrderState.CANCELLED)
                .transition()
                .from(OrderState.INVENTORY_PENDING)
                .event(OrderEvent.INVENTORY_RESERVED)
                .to(OrderState.COMPLETED)
                .transition()
                .from(OrderState.INVENTORY_PENDING)
                .event(OrderEvent.INVENTORY_REJECTED)
                .to(OrderState.PAYMENT_COMPENSATION_PENDING)
                .command(ctx -> new ReleasePaymentCommand(
                        ctx.machineId(),
                        ctx.workflowContext().getString("paymentReservationId").orElse("")))
                .transition()
                .from(OrderState.PAYMENT_COMPENSATION_PENDING)
                .event(OrderEvent.PAYMENT_RELEASED)
                .to(OrderState.CANCELLED)
                .build();
    }
}
