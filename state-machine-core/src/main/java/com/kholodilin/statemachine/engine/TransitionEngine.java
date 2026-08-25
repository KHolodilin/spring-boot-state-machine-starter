package com.kholodilin.statemachine.engine;

import com.kholodilin.statemachine.StateMachineEvent;
import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.definition.StateMachineDefinition;

/**
 * Pure transition calculator. Does not persist, publish or check idempotency.
 */
public interface TransitionEngine {

    /**
     * @param definition static states, events and transitions
     * @param instance   current durable snapshot
     * @param event      incoming event whose type must match {@code definition.eventType()}
     * @return success or rejected; never duplicate
     */
    TransitionResult transition(
            StateMachineDefinition<?, ?> definition, StateMachineInstance instance, StateMachineEvent<?, ?> event);
}
