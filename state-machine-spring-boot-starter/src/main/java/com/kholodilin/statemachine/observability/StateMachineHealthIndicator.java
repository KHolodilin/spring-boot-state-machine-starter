package com.kholodilin.statemachine.observability;

import com.kholodilin.statemachine.async.StateMachineWorkerPool;
import com.kholodilin.statemachine.spi.StateMachineCache;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.time.Duration;
import java.time.Instant;

public final class StateMachineHealthIndicator implements HealthIndicator {

    private final StateMachineCache cache;
    private final StateMachineDispatchQueue queue;
    private final StateMachineWorkerPool workerPool;
    private final StateMachineRequestStore requestStore;
    private final boolean recoveryEnabled;

    public StateMachineHealthIndicator(
            StateMachineCache cache,
            StateMachineDispatchQueue queue,
            StateMachineWorkerPool workerPool,
            StateMachineRequestStore requestStore,
            boolean recoveryEnabled) {
        this.cache = cache;
        this.queue = queue;
        this.workerPool = workerPool;
        this.requestStore = requestStore;
        this.recoveryEnabled = recoveryEnabled;
    }

    @Override
    public Health health() {
        Instant oldest = requestStore.oldestPendingCreatedAt();
        long oldestAgeSeconds = oldest == null ? 0 : Duration.between(oldest, Instant.now()).toSeconds();
        try {
            cache.size();
            return Health.up()
                    .withDetail("cacheSize", cache.size())
                    .withDetail("queueSize", queue.size())
                    .withDetail("queuePressure", queue.pressure())
                    .withDetail("asyncWorkers", workerPool == null ? 0 : workerPool.workerCount())
                    .withDetail("recoveryEnabled", recoveryEnabled)
                    .withDetail("oldestPendingRequestAge", oldestAgeSeconds)
                    .build();
        } catch (RuntimeException ex) {
            return Health.down(ex).build();
        }
    }
}
