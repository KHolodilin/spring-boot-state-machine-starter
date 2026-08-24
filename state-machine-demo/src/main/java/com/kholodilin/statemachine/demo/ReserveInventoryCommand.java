package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.StateMachineCommand;

public record ReserveInventoryCommand(String orderId, String paymentReservationId) implements StateMachineCommand {

    @Override
    public String type() {
        return "ReserveInventory";
    }

    @Override
    public Object payload() {
        return this;
    }
}
