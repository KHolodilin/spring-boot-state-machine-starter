package com.kholodilin.statemachine;

import java.util.Map;
import java.util.Objects;

/**
 * Durable instance snapshot. {@code context} is the JSONB representation.
 *
 * @param machineType definition name
 * @param machineId   instance identifier
 * @param state       current state enum name
 * @param context     workflow JSON; {@code null} is stored as empty
 * @param version     optimistic-lock version
 */
public record StateMachineInstance(
        String machineType,
        String machineId,
        String state,
        Map<String, Object> context,
        long version
) {

    /**
     * Copies {@code context} defensively.
     */
    public StateMachineInstance {
        Objects.requireNonNull(machineType, "machineType");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(state, "state");
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    /**
     * @return mutable wrapper over a copy of {@link #context()}
     */
    public StateMachineContext workflowContext() {
        return MapStateMachineContext.copyOf(context);
    }
}
