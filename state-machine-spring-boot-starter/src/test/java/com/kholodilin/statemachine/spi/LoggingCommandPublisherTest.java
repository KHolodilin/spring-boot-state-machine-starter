package com.kholodilin.statemachine.spi;

import java.util.List;
import java.util.Map;

import com.kholodilin.statemachine.StateMachineInstance;
import org.junit.jupiter.api.Test;

class LoggingCommandPublisherTest {

    @Test
    void publishLogsCommands() {
        LoggingCommandPublisher publisher = new LoggingCommandPublisher();
        publisher.publish(new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0), List.of());
        publisher.publish(
                new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0),
                List.of(new com.kholodilin.statemachine.StateMachineCommand() {
                    @Override
                    public String type() {
                        return "Test";
                    }

                    @Override
                    public Object payload() {
                        return "p";
                    }
                }));
    }
}
