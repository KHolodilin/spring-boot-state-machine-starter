package com.kholodilin.statemachine.observability;

import java.time.Duration;
import java.time.Instant;

import com.kholodilin.statemachine.RejectedReason;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.async.StateMachineWorkerPool;
import com.kholodilin.statemachine.cache.CaffeineStateMachineCache;
import com.kholodilin.statemachine.cache.NoOpStateMachineCache;
import com.kholodilin.statemachine.queue.PartitionedMemoryDispatchQueue;
import com.kholodilin.statemachine.spi.StateMachineCache;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObservabilityComponentsTest {

    @Test
    void metricsRecordTransitionOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PartitionedMemoryDispatchQueue queue = new PartitionedMemoryDispatchQueue(1, 10);
        StateMachineMetrics metrics =
                new StateMachineMetrics(registry, new CaffeineStateMachineCache(10, Duration.ofMinutes(1)), queue);
        metrics.cacheHit();
        metrics.cacheMiss();
        metrics.optimisticLockConflict();
        metrics.asyncSubmitted("order-saga");
        metrics.asyncProcessed("order-saga", "success");
        metrics.asyncFailed("order-saga");
        metrics.recovery(2);
        metrics.transition(
                new TransitionResult.Success("order-saga", "1", "e", "START", "NEW", "PENDING", 1, null, null),
                1_000_000);
        metrics.transition(
                new TransitionResult.Duplicate("order-saga", "1", "e", "START", "NEW", "PENDING"), 1_000_000);
        metrics.transition(
                new TransitionResult.Rejected("order-saga", "1", "e", "START", "NEW", RejectedReason.NO_TRANSITION),
                1_000_000);
        var persist = metrics.startPersist();
        metrics.stopPersist(persist);
        var load = metrics.startLoad();
        metrics.stopLoad(load);
        assertThat(registry.find(StateMachineMetrics.CACHE_HIT).counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void healthIsUpAndDown() {
        StateMachineRequestStore store = mock(StateMachineRequestStore.class);
        when(store.oldestPendingCreatedAt()).thenReturn(Instant.now().minusSeconds(5));
        StateMachineWorkerPool pool = mock(StateMachineWorkerPool.class);
        when(pool.workerCount()).thenReturn(2);
        StateMachineHealthIndicator indicator = new StateMachineHealthIndicator(
                new NoOpStateMachineCache(), new PartitionedMemoryDispatchQueue(1, 10), pool, store, true);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);

        StateMachineCache failing = mock(StateMachineCache.class);
        when(failing.size()).thenThrow(new RuntimeException("cache"));
        StateMachineHealthIndicator down =
                new StateMachineHealthIndicator(failing, new PartitionedMemoryDispatchQueue(1, 10), null, store, false);
        assertThat(down.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void distributionRefreshSwallowsQueryErrors() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenThrow(new RuntimeException("closed"));
        new StateDistributionMetrics(jdbcTemplate, new SimpleMeterRegistry()).refresh();
    }

    @Test
    void distributionRefreshRegistersRows() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getString(1)).thenReturn("order-saga");
            when(rs.getString(2)).thenReturn("NEW");
            when(rs.getLong(3)).thenReturn(2L);
            return java.util.List.of(mapper.mapRow(rs, 0));
        });
        new StateDistributionMetrics(jdbcTemplate, new SimpleMeterRegistry()).refresh();
    }

    @Test
    void healthOldestPendingAgeIsZeroWhenEmpty() {
        StateMachineRequestStore store = mock(StateMachineRequestStore.class);
        when(store.oldestPendingCreatedAt()).thenReturn(null);
        StateMachineHealthIndicator indicator = new StateMachineHealthIndicator(
                new NoOpStateMachineCache(), new PartitionedMemoryDispatchQueue(1, 10), null, store, false);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("oldestPendingRequestAge", 0L);
    }
}
