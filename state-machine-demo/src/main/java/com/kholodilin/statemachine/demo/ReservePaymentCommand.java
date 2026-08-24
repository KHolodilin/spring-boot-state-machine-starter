package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.StateMachineCommand;

public record ReservePaymentCommand(String orderId) implements StateMachineCommand {

    @Override
    public String type() {
        return "ReservePayment";
    }

    @Override
    public Object payload() {
        return this;
    }
}
