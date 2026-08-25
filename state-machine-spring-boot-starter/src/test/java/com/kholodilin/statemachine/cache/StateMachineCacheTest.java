package com.kholodilin.statemachine.cache;

import java.time.Duration;
import java.util.Map;

import com.kholodilin.statemachine.StateMachineInstance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineCacheTest {

    @Test
    void caffeinePutGetInvalidateAndStats() {
        CaffeineStateMachineCache cache = new CaffeineStateMachineCache(10, Duration.ofMinutes(5));
        StateMachineInstance instance = new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0);
        cache.put(instance);
        assertThat(cache.get("order-saga", "1")).contains(instance);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.stats().hitCount()).isGreaterThanOrEqualTo(0);
        cache.invalidate("order-saga", "1");
        assertThat(cache.get("order-saga", "1")).isEmpty();
    }

    @Test
    void noOpNeverStores() {
        NoOpStateMachineCache cache = new NoOpStateMachineCache();
        cache.put(new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0));
        assertThat(cache.get("order-saga", "1")).isEmpty();
        cache.invalidate("order-saga", "1");
        assertThat(cache.size()).isZero();
    }
}
