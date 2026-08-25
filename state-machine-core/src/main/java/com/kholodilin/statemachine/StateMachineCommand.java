package com.kholodilin.statemachine;

/**
 * Intent to execute after a successful transition. Transport is decided by a publisher.
 */
public interface StateMachineCommand {

    /**
     * @return short command name used in logs and Outbox rows
     */
    String type();

    /**
     * @return serializable payload, often the command record itself
     */
    Object payload();
}
