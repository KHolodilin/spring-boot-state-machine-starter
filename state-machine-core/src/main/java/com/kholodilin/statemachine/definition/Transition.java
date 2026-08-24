package com.kholodilin.statemachine.definition;

import com.kholodilin.statemachine.ContextUpdater;
import com.kholodilin.statemachine.Guard;
import com.kholodilin.statemachine.StateMachineCommandFactory;

import java.util.List;

/**
 * Static transition: from + event + optional guard -> to + optional context update + 0..N commands.
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

    public Transition {
        commandFactories = commandFactories == null ? List.of() : List.copyOf(commandFactories);
    }

    public boolean hasGuard() {
        return guard != null;
    }
}
