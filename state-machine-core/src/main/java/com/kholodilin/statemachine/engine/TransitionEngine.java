package com.kholodilin.statemachine.engine;

import com.kholodilin.statemachine.StateMachineEvent;
import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.definition.StateMachineDefinition;

/**
 * Pure transition calculator. Does not persist, publish or check idempotency.
 */
public interface TransitionEngine {

    TransitionResult transition(
            StateMachineDefinition<?, ?> definition,
            StateMachineInstance instance,
            StateMachineEvent<?, ?> event);
}
