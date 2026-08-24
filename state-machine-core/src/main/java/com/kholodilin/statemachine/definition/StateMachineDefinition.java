package com.kholodilin.statemachine.definition;

import com.kholodilin.statemachine.ContextUpdater;
import com.kholodilin.statemachine.Guard;
import com.kholodilin.statemachine.StateMachineCommandFactory;
import com.kholodilin.statemachine.exception.InvalidDefinitionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Static description of states, events and transitions. Created at application startup, never loaded from DB.
 */
public final class StateMachineDefinition<S extends Enum<S>, E extends Enum<E>> {

    private final String machineType;
    private final Class<S> stateType;
    private final Class<E> eventType;
    private final S initialState;
    private final Map<E, Class<?>> payloadTypes;
    private final List<Transition<S, E, ?>> transitions;

    private StateMachineDefinition(
            String machineType,
            Class<S> stateType,
            Class<E> eventType,
            S initialState,
            Map<E, Class<?>> payloadTypes,
            List<Transition<S, E, ?>> transitions) {
        this.machineType = machineType;
        this.stateType = stateType;
        this.eventType = eventType;
        this.initialState = initialState;
        this.payloadTypes = Map.copyOf(payloadTypes);
        this.transitions = List.copyOf(transitions);
    }

    public String machineType() {
        return machineType;
    }

    public Class<S> stateType() {
        return stateType;
    }

    public Class<E> eventType() {
        return eventType;
    }

    public S initialState() {
        return initialState;
    }

    public Class<?> payloadType(E event) {
        Class<?> type = payloadTypes.get(event);
        if (type == null) {
            throw new InvalidDefinitionException(
                    "No payload class registered for event " + event + " on machine " + machineType);
        }
        return type;
    }

    public Optional<Class<?>> findPayloadType(String eventName) {
        for (Map.Entry<E, Class<?>> entry : payloadTypes.entrySet()) {
            if (entry.getKey().name().equals(eventName)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public E eventFromName(String eventName) {
        return Enum.valueOf(eventType, eventName);
    }

    public S stateFromName(String stateName) {
        return Enum.valueOf(stateType, stateName);
    }

    public List<Transition<S, E, ?>> transitions() {
        return transitions;
    }

    @SuppressWarnings("unchecked")
    public List<Transition<S, E, Object>> matching(S from, E event) {
        List<Transition<S, E, Object>> matches = new ArrayList<>();
        for (Transition<S, E, ?> transition : transitions) {
            if (transition.from() == from && transition.event() == event) {
                matches.add((Transition<S, E, Object>) transition);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public static <S extends Enum<S>, E extends Enum<E>> Builder<S, E> builder(
            String machineType,
            Class<S> stateType,
            Class<E> eventType) {
        return new Builder<>(machineType, stateType, eventType);
    }

    public static final class Builder<S extends Enum<S>, E extends Enum<E>> {

        private final String machineType;
        private final Class<S> stateType;
        private final Class<E> eventType;
        private final Map<E, Class<?>> payloadTypes;
        private final List<Transition<S, E, ?>> transitions = new ArrayList<>();
        private S initialState;

        private Builder(String machineType, Class<S> stateType, Class<E> eventType) {
            if (machineType == null || machineType.isBlank()) {
                throw new InvalidDefinitionException("machineType must not be blank");
            }
            this.machineType = machineType;
            this.stateType = Objects.requireNonNull(stateType, "stateType");
            this.eventType = Objects.requireNonNull(eventType, "eventType");
            this.payloadTypes = new EnumMap<>(eventType);
        }

        public Builder<S, E> initial(S state) {
            this.initialState = Objects.requireNonNull(state, "initial");
            return this;
        }

        public Builder<S, E> payload(E event, Class<?> payloadClass) {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(payloadClass, "payloadClass");
            Class<?> previous = payloadTypes.put(event, payloadClass);
            if (previous != null) {
                throw new InvalidDefinitionException(
                        "Payload class already registered for event " + event + " on machine " + machineType);
            }
            return this;
        }

        public TransitionFrom<S, E> transition() {
            return new TransitionFrom<>(this);
        }

        public StateMachineDefinition<S, E> build() {
            if (initialState == null) {
                throw new InvalidDefinitionException("initial state is required for machine " + machineType);
            }
            for (Transition<S, E, ?> transition : transitions) {
                if (!payloadTypes.containsKey(transition.event())) {
                    throw new InvalidDefinitionException(
                            "Event " + transition.event() + " is used in a transition but has no .payload(...) on machine "
                                    + machineType);
                }
            }
            return new StateMachineDefinition<>(
                    machineType, stateType, eventType, initialState, payloadTypes, transitions);
        }

        void addTransition(Transition<S, E, ?> transition) {
            transitions.add(transition);
        }

        @SuppressWarnings("unchecked")
        <P> Class<P> requirePayload(E event) {
            Class<?> type = payloadTypes.get(event);
            if (type == null) {
                throw new InvalidDefinitionException(
                        "Register .payload(" + event + ", payloadClass) before using the event in a transition on machine "
                                + machineType);
            }
            return (Class<P>) type;
        }
    }

    public static final class TransitionFrom<S extends Enum<S>, E extends Enum<E>> {

        private final Builder<S, E> parent;

        private TransitionFrom(Builder<S, E> parent) {
            this.parent = parent;
        }

        public TransitionEvent<S, E> from(S state) {
            return new TransitionEvent<>(parent, Objects.requireNonNull(state, "from"));
        }
    }

    public static final class TransitionEvent<S extends Enum<S>, E extends Enum<E>> {

        private final Builder<S, E> parent;
        private final S from;

        private TransitionEvent(Builder<S, E> parent, S from) {
            this.parent = parent;
            this.from = from;
        }

        public <P> TransitionSpec<S, E, P> event(E event) {
            Objects.requireNonNull(event, "event");
            return event(event, parent.requirePayload(event));
        }

        public <P> TransitionSpec<S, E, P> event(E event, Class<P> payloadClass) {
            Objects.requireNonNull(event, "event");
            Objects.requireNonNull(payloadClass, "payloadClass");
            Class<?> registered = parent.requirePayload(event);
            if (!registered.equals(payloadClass)) {
                throw new InvalidDefinitionException(
                        "Payload class " + payloadClass.getName() + " does not match registered "
                                + registered.getName() + " for event " + event + " on machine " + parent.machineType);
            }
            return new TransitionSpec<>(parent, from, event, payloadClass);
        }
    }

    public static final class TransitionSpec<S extends Enum<S>, E extends Enum<E>, P> {

        private final Builder<S, E> parent;
        private final S from;
        private final E event;
        private final Class<P> payloadType;
        private final List<StateMachineCommandFactory<S, E, P>> commandFactories = new ArrayList<>();
        private Guard<S, E, P> guard;
        private S to;
        private ContextUpdater<E, P> contextUpdater;

        private TransitionSpec(Builder<S, E> parent, S from, E event, Class<P> payloadType) {
            this.parent = parent;
            this.from = from;
            this.event = event;
            this.payloadType = payloadType;
        }

        public TransitionSpec<S, E, P> when(Guard<S, E, P> guard) {
            this.guard = Objects.requireNonNull(guard, "guard");
            return this;
        }

        public TransitionSpec<S, E, P> to(S state) {
            this.to = Objects.requireNonNull(state, "to");
            return this;
        }

        public TransitionSpec<S, E, P> updateContext(ContextUpdater<E, P> updater) {
            this.contextUpdater = Objects.requireNonNull(updater, "updater");
            return this;
        }

        public TransitionSpec<S, E, P> command(StateMachineCommandFactory<S, E, P> factory) {
            commandFactories.add(Objects.requireNonNull(factory, "factory"));
            return this;
        }

        public TransitionFrom<S, E> transition() {
            complete();
            return parent.transition();
        }

        public Builder<S, E> payload(E nextEvent, Class<?> payloadClass) {
            complete();
            return parent.payload(nextEvent, payloadClass);
        }

        public StateMachineDefinition<S, E> build() {
            complete();
            return parent.build();
        }

        private void complete() {
            if (to == null) {
                throw new InvalidDefinitionException(
                        "Transition " + from + " + " + event + " is missing .to(...) on machine " + parent.machineType);
            }
            parent.addTransition(new Transition<>(
                    from, event, payloadType, guard, to, contextUpdater, List.copyOf(commandFactories)));
        }
    }
}
