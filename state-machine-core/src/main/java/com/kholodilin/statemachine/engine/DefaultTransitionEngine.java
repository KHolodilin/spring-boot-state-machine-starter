package com.kholodilin.statemachine.engine;

import com.kholodilin.statemachine.ContextUpdater;
import com.kholodilin.statemachine.DefaultTransitionContext;
import com.kholodilin.statemachine.MapStateMachineContext;
import com.kholodilin.statemachine.RejectedReason;
import com.kholodilin.statemachine.StateMachineCommand;
import com.kholodilin.statemachine.StateMachineCommandFactory;
import com.kholodilin.statemachine.StateMachineContext;
import com.kholodilin.statemachine.StateMachineEvent;
import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.TransitionContext;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.definition.Transition;
import com.kholodilin.statemachine.exception.AmbiguousTransitionException;
import com.kholodilin.statemachine.exception.EventTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

public final class DefaultTransitionEngine implements TransitionEngine {

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TransitionResult transition(
            StateMachineDefinition<?, ?> definition,
            StateMachineInstance instance,
            StateMachineEvent<?, ?> event) {
        return doTransition((StateMachineDefinition) definition, instance, event);
    }

    @SuppressWarnings("unchecked")
    private <S extends Enum<S>, E extends Enum<E>> TransitionResult doTransition(
            StateMachineDefinition<S, E> definition,
            StateMachineInstance instance,
            StateMachineEvent<?, ?> rawEvent) {
        if (!definition.eventType().isInstance(rawEvent.type())) {
            throw new EventTypeMismatchException(
                    definition.machineType(), definition.eventType(), rawEvent.type().getClass());
        }
        @SuppressWarnings("unchecked")
        StateMachineEvent<E, Object> event = (StateMachineEvent<E, Object>) rawEvent;
        S current = definition.stateFromName(instance.state());
        List<Transition<S, E, Object>> candidates = definition.matching(current, event.type());
        if (candidates.isEmpty()) {
            return rejected(definition, instance, event, RejectedReason.NO_TRANSITION);
        }

        List<Transition<S, E, Object>> matched = new ArrayList<>();
        StateMachineContext currentContext = MapStateMachineContext.copyOf(instance.context());
        TransitionContext<S, E, Object> guardContext = new DefaultTransitionContext<>(
                definition.machineType(),
                instance.machineId(),
                current,
                currentContext,
                event);
        for (Transition<S, E, Object> candidate : candidates) {
            if (candidate.guard() == null || candidate.guard().test(guardContext)) {
                matched.add(candidate);
            }
        }
        if (matched.isEmpty()) {
            return rejected(definition, instance, event, RejectedReason.GUARD_NOT_MATCHED);
        }
        if (matched.size() > 1) {
            throw new AmbiguousTransitionException(
                    "Multiple transitions matched for " + definition.machineType()
                            + " state " + current + " event " + event.type());
        }

        Transition<S, E, Object> chosen = matched.getFirst();
        StateMachineContext updated = applyUpdater(currentContext, event, chosen.contextUpdater());
        TransitionContext<S, E, Object> commandContext = new DefaultTransitionContext<>(
                definition.machineType(),
                instance.machineId(),
                current,
                updated,
                event);
        List<StateMachineCommand> commands = new ArrayList<>();
        for (StateMachineCommandFactory<S, E, Object> factory : chosen.commandFactories()) {
            commands.add(factory.create(commandContext));
        }
        return new TransitionResult.Success(
                definition.machineType(),
                instance.machineId(),
                event.eventId(),
                event.type().toString(),
                current.name(),
                chosen.to().name(),
                instance.version() + 1,
                commands,
                updated.asMap());
    }

    private static <E, P> StateMachineContext applyUpdater(
            StateMachineContext current,
            StateMachineEvent<E, P> event,
            ContextUpdater<E, P> updater) {
        if (updater == null) {
            return current;
        }
        StateMachineContext copy = MapStateMachineContext.copyOf(current.asMap());
        return updater.update(copy, event);
    }

    private static <S extends Enum<S>, E extends Enum<E>> TransitionResult.Rejected rejected(
            StateMachineDefinition<S, E> definition,
            StateMachineInstance instance,
            StateMachineEvent<E, ?> event,
            RejectedReason reason) {
        return new TransitionResult.Rejected(
                definition.machineType(),
                instance.machineId(),
                event.eventId(),
                event.type().toString(),
                instance.state(),
                reason);
    }
}
