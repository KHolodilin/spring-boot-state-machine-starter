package com.kholodilin.statemachine.demo;

import com.kholodilin.statemachine.StateMachineEvent;
import com.kholodilin.statemachine.StateMachineService;
import com.kholodilin.statemachine.TransitionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class DemoRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    @Bean
    @ConditionalOnProperty(name = "demo.auto-run", havingValue = "true")
    ApplicationRunner runHappyPath(StateMachineService service) {
        return args -> {
            String orderId = "order-demo";
            TransitionResult started = service.send(
                    "order-saga",
                    new StateMachineEvent<>(
                            UUID.randomUUID().toString(),
                            orderId,
                            OrderSagaConfiguration.OrderEvent.START,
                            null));
            log.info("START -> {}", started.outcome());
            TransitionResult reserved = service.send(
                    "order-saga",
                    new StateMachineEvent<>(
                            UUID.randomUUID().toString(),
                            orderId,
                            OrderSagaConfiguration.OrderEvent.PAYMENT_RESERVED,
                            new PaymentReservedPayload("PAY-1")));
            log.info("PAYMENT_RESERVED -> {}", reserved.outcome());
        };
    }
}
