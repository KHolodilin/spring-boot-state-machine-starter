package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.StateMachineCommand;

/**
 * Command emitted after payment is reserved: reserve inventory.
 *
 * @param orderId               machine id of the order saga
 * @param paymentReservationId  value from workflow context
 */
public record ReserveInventoryCommand(String orderId, String paymentReservationId) implements StateMachineCommand {

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return "ReserveInventory";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object payload() {
        return this;
    }
}
