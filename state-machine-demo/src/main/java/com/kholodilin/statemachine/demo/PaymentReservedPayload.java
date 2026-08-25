package com.kholodilin.statemachine.demo;

/**
 * Payload of {@link OrderSagaConfiguration.OrderEvent#PAYMENT_RESERVED}.
 *
 * @param reservationId payment reservation identifier stored in workflow context
 */
public record PaymentReservedPayload(String reservationId) {}
