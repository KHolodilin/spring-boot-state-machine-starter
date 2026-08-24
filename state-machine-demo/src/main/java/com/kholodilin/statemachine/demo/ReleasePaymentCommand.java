package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.StateMachineCommand;

public record ReleasePaymentCommand(String orderId, String paymentReservationId) implements StateMachineCommand {

    @Override
    public String type() {
        return "ReleasePayment";
    }

    @Override
    public Object payload() {
        return this;
    }
}
