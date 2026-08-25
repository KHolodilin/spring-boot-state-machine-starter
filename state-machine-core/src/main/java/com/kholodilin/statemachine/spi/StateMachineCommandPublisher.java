package com.kholodilin.statemachine.spi;

import java.util.Collection;

import com.kholodilin.statemachine.StateMachineCommand;
import com.kholodilin.statemachine.StateMachineInstance;

/**
 * Publishes commands produced by a successful transition. Must run in the same JDBC
 * transaction as the instance/event writes. Replace the default logger with an Outbox adapter.
 */
public interface StateMachineCommandPublisher {

    /**
     * @param machine  instance after the transition
     * @param commands {@code 0..N} intents; empty is valid
     */
    void publish(StateMachineInstance machine, Collection<StateMachineCommand> commands);
}
