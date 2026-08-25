package com.kholodilin.statemachine;

/**
 * Expected outcome of {@code send()}. Infrastructure failures use exceptions instead.
 */
public enum TransitionOutcome {
    /** State changed and commands were produced. */
    SUCCESS,
    /** This {@code eventId} was already processed. */
    DUPLICATE,
    /** No matching transition or all guards failed. */
    REJECTED
}
