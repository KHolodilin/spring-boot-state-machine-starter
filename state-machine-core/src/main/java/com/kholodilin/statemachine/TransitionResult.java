package com.kholodilin.statemachine;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Expected outcomes of {@code send()}. Infrastructure failures use exceptions, not this type.
 */
public sealed interface TransitionResult
        permits TransitionResult.Success, TransitionResult.Duplicate, TransitionResult.Rejected {

    String machineType();

    String machineId();

    String eventId();

    String eventType();

    TransitionOutcome outcome();

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

        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.SUCCESS;
        }
    }

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

        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.DUPLICATE;
        }
    }

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

        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.REJECTED;
        }
    }
}
