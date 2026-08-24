package com.kholodilin.statemachine.spi;

import java.time.Duration;

public interface StateMachineDispatchQueue {

    boolean offer(long requestId, String machineId);

    Long poll(int partition, Duration timeout) throws InterruptedException;

    int partitions();

    int size();

    int capacity();

    double pressure();
}
