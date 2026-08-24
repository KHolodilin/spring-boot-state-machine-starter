package com.kholodilin.statemachine.spi;

import java.util.Optional;

public interface StateMachineEventStore {

    boolean exists(String eventId);

    Optional<ProcessedStateMachineEvent> find(String eventId);

    void append(ProcessedStateMachineEvent event);
}
