package com.kholodilin.statemachine;

/**
 * Intent to execute after a successful transition. Transport is decided by a publisher.
 */
public interface StateMachineCommand {

    String type();

    Object payload();
}
