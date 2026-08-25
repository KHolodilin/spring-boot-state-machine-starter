package com.kholodilin.statemachine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expected outcomes of {@code send()}. Infrastructure failures use exceptions, not this type.
 */
public sealed interface TransitionResult
        permits TransitionResult.Success, TransitionResult.Duplicate, TransitionResult.Rejected {

    /**
     * @return definition name
     */
    String machineType();

    /**
     * @return instance identifier
     */
    String machineId();

    /**
     * @return idempotency key of the processed event
     */
    String eventId();

    /**
     * @return event enum name
     */
    String eventType();

    /**
     * @return success, duplicate or rejected
     */
    TransitionOutcome outcome();

    /**
     * State changed and commands were produced.
     *
     * @param machineType definition name
     * @param machineId   instance identifier
     * @param eventId     processed event
     * @param eventType   event enum name
     * @param fromState   state before the transition
     * @param toState     state after the transition
     * @param version     optimistic-lock version after persist
     * @param commands    intents to publish in the same transaction
     * @param context     workflow context after {@code ContextUpdater}
     */
    record Success(
            String machineType,
            String machineId,
            String eventId,
            String eventType,
            String fromState,
            String toState,
            long version,
            List<StateMachineCommand> commands,
            Map<String, Object> context
    ) implements TransitionResult {

        public Success {
            Objects.requireNonNull(machineType, "machineType");
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(fromState, "fromState");
            Objects.requireNonNull(toState, "toState");
            commands = commands == null ? List.of() : List.copyOf(commands);
            context = context == null ? Map.of() : Map.copyOf(context);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.SUCCESS;
        }
    }

    /**
     * This {@code eventId} was already recorded. Not an application error.
     *
     * @param machineType definition name
     * @param machineId   instance identifier
     * @param eventId     original event
     * @param eventType   original event type
     * @param fromState   states stored with the first processing
     * @param toState     states stored with the first processing
     */
    record Duplicate(
            String machineType,
            String machineId,
            String eventId,
            String eventType,
            String fromState,
            String toState
    ) implements TransitionResult {

        public Duplicate {
            Objects.requireNonNull(machineType, "machineType");
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(fromState, "fromState");
            Objects.requireNonNull(toState, "toState");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.DUPLICATE;
        }
    }

    /**
     * No matching transition, or every matching guard returned {@code false}.
     * The event is still written to history so a later send of the same {@code eventId} is {@code Duplicate}.
     *
     * @param machineType definition name
     * @param machineId   instance identifier
     * @param eventId     processed event
     * @param eventType   event enum name
     * @param state       current instance state (unchanged)
     * @param reason      why the engine rejected the event
     */
    record Rejected(
            String machineType,
            String machineId,
            String eventId,
            String eventType,
            String state,
            RejectedReason reason
    ) implements TransitionResult {

        public Rejected {
            Objects.requireNonNull(machineType, "machineType");
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.REJECTED;
        }
    }
}
