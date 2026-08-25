package com.kholodilin.statemachine.spi;

import java.util.Collection;

import com.kholodilin.statemachine.StateMachineCommand;
import com.kholodilin.statemachine.StateMachineInstance;

/**
 * Publisher that discards commands. Useful in tests; production should use Outbox or logging.
 */
public final class NoOpCommandPublisher implements StateMachineCommandPublisher {

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(StateMachineInstance machine, Collection<StateMachineCommand> commands) {
        // intentionally empty
    }
}
