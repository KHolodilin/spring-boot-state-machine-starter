package com.kholodilin.statemachine.spi;

import com.kholodilin.statemachine.StateMachineCommand;
import com.kholodilin.statemachine.StateMachineInstance;

import java.util.Collection;

public interface StateMachineCommandPublisher {

    void publish(StateMachineInstance machine, Collection<StateMachineCommand> commands);
}
