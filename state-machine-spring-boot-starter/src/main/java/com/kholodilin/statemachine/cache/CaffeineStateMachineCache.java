package com.kholodilin.statemachine.cache;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.spi.StateMachineCache;

/**
 * Caffeine hot set of instance snapshots. A cache hit still uses optimistic {@code UPDATE ... WHERE version}.
 */
public final class CaffeineStateMachineCache implements StateMachineCache {

    private final Cache<String, StateMachineInstance> cache;

    /**
     * @param maxSize           Caffeine {@code maximumSize}
     * @param expireAfterAccess idle eviction
     */
    public CaffeineStateMachineCache(long maxSize, Duration expireAfterAccess) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expireAfterAccess)
                .recordStats()
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<StateMachineInstance> get(String machineType, String machineId) {
        return Optional.ofNullable(cache.getIfPresent(key(machineType, machineId)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(StateMachineInstance instance) {
        cache.put(key(instance.machineType(), instance.machineId()), instance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidate(String machineType, String machineId) {
        cache.invalidate(key(machineType, machineId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    /**
     * @return Caffeine stats for eviction gauges
     */
    public CacheStats stats() {
        return cache.stats();
    }

    private static String key(String machineType, String machineId) {
        return machineType + '\0' + machineId;
    }
}
