package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.StateMachineCommand;

/**
 * Compensation command after inventory rejection: release the payment reservation.
 *
 * @param orderId               machine id of the order saga
 * @param paymentReservationId  reservation to release
 */
public record ReleasePaymentCommand(String orderId, String paymentReservationId) implements StateMachineCommand {

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return "ReleasePayment";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object payload() {
        return this;
    }
}
