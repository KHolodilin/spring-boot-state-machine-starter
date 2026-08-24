package com.kholodilin.statemachine.observability;

import com.kholodilin.statemachine.TransitionOutcome;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.cache.CaffeineStateMachineCache;
import com.kholodilin.statemachine.spi.StateMachineCache;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

public final class StateMachineMetrics {

    public static final String TRANSITION_TOTAL = "state_machine_transition_total";
    public static final String TRANSITION_SECONDS = "state_machine_transition_seconds";
    public static final String OPTIMISTIC_LOCK = "state_machine_optimistic_lock_conflict_total";
    public static final String ASYNC_SUBMITTED = "state_machine_async_submitted_total";
    public static final String ASYNC_PROCESSED = "state_machine_async_processed_total";
    public static final String ASYNC_FAILED = "state_machine_async_failed_total";
    public static final String ASYNC_RECOVERY = "state_machine_async_recovery_total";
    public static final String CACHE_HIT = "state_machine_cache_hit_total";
    public static final String CACHE_MISS = "state_machine_cache_miss_total";
    public static final String CACHE_EVICTION = "state_machine_cache_eviction_total";
    public static final String LOAD_SECONDS = "state_machine_load_seconds";
    public static final String PERSIST_SECONDS = "state_machine_persist_seconds";

    private final MeterRegistry registry;

    public StateMachineMetrics(
            MeterRegistry registry,
            StateMachineCache cache,
            StateMachineDispatchQueue queue) {
        this.registry = registry;
        registry.gauge("state_machine_cache_size", cache, StateMachineCache::size);
        registry.gauge("state_machine_queue_size", queue, StateMachineDispatchQueue::size);
        registry.gauge("state_machine_queue_pressure", queue, StateMachineDispatchQueue::pressure);
        if (cache instanceof CaffeineStateMachineCache caffeine) {
            registry.gauge(CACHE_EVICTION, caffeine, value -> value.stats().evictionCount());
        }
    }

    public void cacheHit() {
        registry.counter(CACHE_HIT).increment();
    }

    public void cacheMiss() {
        registry.counter(CACHE_MISS).increment();
    }

    public void optimisticLockConflict() {
        registry.counter(OPTIMISTIC_LOCK).increment();
    }

    public void asyncSubmitted(String machineType) {
        Counter.builder(ASYNC_SUBMITTED)
                .tag("machineType", machineType)
                .register(registry)
                .increment();
    }

    public void asyncProcessed(String machineType, String result) {
        Counter.builder(ASYNC_PROCESSED)
                .tag("machineType", machineType)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void asyncFailed(String machineType) {
        Counter.builder(ASYNC_FAILED)
                .tag("machineType", machineType)
                .register(registry)
                .increment();
    }

    public void recovery(int count) {
        registry.counter(ASYNC_RECOVERY).increment(count);
    }

    public void transition(TransitionResult result, long nanos) {
        String from = "";
        String to = "";
        if (result instanceof TransitionResult.Success success) {
            from = success.fromState();
            to = success.toState();
        } else if (result instanceof TransitionResult.Duplicate duplicate) {
            from = duplicate.fromState();
            to = duplicate.toState();
        } else if (result instanceof TransitionResult.Rejected rejected) {
            from = rejected.state();
            to = rejected.state();
        }
        Counter.builder(TRANSITION_TOTAL)
                .tag("machineType", result.machineType())
                .tag("fromState", from)
                .tag("toState", to)
                .tag("eventType", result.eventType())
                .tag("result", resultTag(result.outcome()))
                .register(registry)
                .increment();
        Timer.builder(TRANSITION_SECONDS)
                .tag("machineType", result.machineType())
                .tag("eventType", result.eventType())
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    public Timer.Sample startPersist() {
        return Timer.start(registry);
    }

    public void stopPersist(Timer.Sample sample) {
        sample.stop(Timer.builder(PERSIST_SECONDS).register(registry));
    }

    public Timer.Sample startLoad() {
        return Timer.start(registry);
    }

    public void stopLoad(Timer.Sample sample) {
        sample.stop(Timer.builder(LOAD_SECONDS).register(registry));
    }

    private static String resultTag(TransitionOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> "success";
            case DUPLICATE -> "duplicate";
            case REJECTED -> "rejected";
        };
    }
}
