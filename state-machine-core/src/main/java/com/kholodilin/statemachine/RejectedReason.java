package com.kholodilin.statemachine;

/**
 * Why the engine returned {@link TransitionResult.Rejected}.
 */
public enum RejectedReason {
    /** Current state has no transition for this event. */
    NO_TRANSITION,
    /** Transitions exist but every guard returned {@code false}. */
    GUARD_NOT_MATCHED
}
