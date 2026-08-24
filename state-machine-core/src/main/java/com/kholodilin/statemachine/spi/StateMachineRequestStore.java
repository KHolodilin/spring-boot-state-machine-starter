package com.kholodilin.statemachine.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StateMachineRequestStore {

    long append(StateMachineRequest request);

    Optional<StateMachineRequest> findById(long id);

    Optional<StateMachineRequest> findByEventId(String eventId);

    List<StateMachineRequest> claimRecoverable(String lockedBy, Instant lockedUntil, int batchSize);

    boolean claim(long id, String lockedBy, Instant lockedUntil);

    void markDone(long id);

    void markFailed(long id, int retryCount);

    void markDead(long id);

    Instant oldestPendingCreatedAt();
}
