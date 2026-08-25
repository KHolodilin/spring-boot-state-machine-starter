package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.StateMachineCommand;

/**
 * Command emitted after {@code START}: reserve payment for the order.
 *
 * @param orderId machine id of the order saga
 */
public record ReservePaymentCommand(String orderId) implements StateMachineCommand {

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return "ReservePayment";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object payload() {
        return this;
    }
}
