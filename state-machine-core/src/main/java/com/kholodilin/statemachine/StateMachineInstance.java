package com.kholodilin.statemachine;

import java.util.Map;
import java.util.Objects;

/**
 * Durable instance snapshot. {@code context} is the JSONB representation.
 */
public record StateMachineInstance(
        String machineType,
        String machineId,
        String state,
        Map<String, Object> context,
        long version
) {

    public StateMachineInstance {
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(state, "state");
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public StateMachineContext workflowContext() {
        return MapStateMachineContext.copyOf(context);
    }
}
