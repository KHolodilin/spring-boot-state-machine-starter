package com.kholodilin.statemachine.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.spi.StateMachineCache;

import java.time.Duration;
import java.util.Optional;

public final class CaffeineStateMachineCache implements StateMachineCache {

    private final Cache<String, StateMachineInstance> cache;

    public CaffeineStateMachineCache(long maxSize, Duration expireAfterAccess) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expireAfterAccess)
                .recordStats()
                .build();
    }

    @Override
    public Optional<StateMachineInstance> get(String machineType, String machineId) {
        return Optional.ofNullable(cache.getIfPresent(key(machineType, machineId)));
    }

    @Override
    public void put(StateMachineInstance instance) {
        cache.put(key(instance.machineType(), instance.machineId()), instance);
    }

    @Override
    public void invalidate(String machineType, String machineId) {
        cache.invalidate(key(machineType, machineId));
    }

    @Override
    public long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    public CacheStats stats() {
        return cache.stats();
    }

    private static String key(String machineType, String machineId) {
        return machineType + '\0' + machineId;
    }
}
