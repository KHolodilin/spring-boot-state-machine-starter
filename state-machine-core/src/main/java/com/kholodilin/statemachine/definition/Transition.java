package com.kholodilin.statemachine.definition;

import com.kholodilin.statemachine.ContextUpdater;
import com.kholodilin.statemachine.Guard;
import com.kholodilin.statemachine.StateMachineCommandFactory;

import java.util.List;

/**
 * Static transition: from + event + optional guard -&gt; to + optional context update + 0..N commands.
 *
 * @param from              source state
 * @param event             triggering event
 * @param payloadType       class registered via {@code .payload(...)}
 * @param guard             {@code null} means always allowed
 * @param to                target state
 * @param contextUpdater    {@code null} leaves context unchanged
 * @param commandFactories  zero or more command builders
 */
public record Transition<S extends Enum<S>, E extends Enum<E>, P>(
        S from,
        E event,
        Class<P> payloadType,
        Guard<S, E, P> guard,
        S to,
        ContextUpdater<E, P> contextUpdater,
        List<StateMachineCommandFactory<S, E, P>> commandFactories
) {

    /**
     * Copies {@code commandFactories} defensively.
     */
    public Transition {
        commandFactories = commandFactories == null ? List.of() : List.copyOf(commandFactories);
    }

    /**
     * @return {@code true} when a guard predicate is attached
     */
    public boolean hasGuard() {
        return guard != null;
    }
}
