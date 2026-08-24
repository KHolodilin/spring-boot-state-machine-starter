package com.kholodilin.statemachine.spi;

import com.kholodilin.statemachine.StateMachineCommand;
import com.kholodilin.statemachine.StateMachineInstance;

import java.util.Collection;

public final class NoOpCommandPublisher implements StateMachineCommandPublisher {

    @Override
    public void publish(StateMachineInstance machine, Collection<StateMachineCommand> commands) {
        // intentionally empty
    }
}
