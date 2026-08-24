package com.kholodilin.statemachine.spi;

import com.kholodilin.statemachine.StateMachineCommand;
import com.kholodilin.statemachine.StateMachineInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Default publisher that logs commands. Replace with a transactional Outbox publisher in production.
 * Commands must be appended in the same JDBC transaction as the state transition.
 */
public final class LoggingCommandPublisher implements StateMachineCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingCommandPublisher.class);

    @Override
    public void publish(StateMachineInstance machine, Collection<StateMachineCommand> commands) {
        for (StateMachineCommand command : commands) {
            log.info(
                    "state machine command machineType={} machineId={} type={} payload={}",
                    machine.machineType(),
                    machine.machineId(),
                    command.type(),
                    command.payload());
        }
    }
}
