package com.kholodilin.statemachine.async;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.kholodilin.statemachine.observability.StateMachineMetrics;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateMachineRecoveryWorkerTest {

    @Test
    void emptyClaimReturnsZero() {
        StateMachineRequestStore store = mock(StateMachineRequestStore.class);
        StateMachineDispatchQueue queue = mock(StateMachineDispatchQueue.class);
        StateMachineMetrics metrics = mock(StateMachineMetrics.class);
        when(store.claimRecoverable(anyString(), any(), anyInt())).thenReturn(List.of());

        StateMachineRecoveryWorker worker = worker(store, queue, metrics);
        assertThat(worker.recover()).isZero();
        verify(store, never()).clearLease(any());
        verify(metrics, never()).recovery(anyInt());
    }

    @Test
    void claimsClearsLeaseAndOffersBatch() {
        StateMachineRequestStore store = mock(StateMachineRequestStore.class);
        StateMachineDispatchQueue queue = mock(StateMachineDispatchQueue.class);
        StateMachineMetrics metrics = mock(StateMachineMetrics.class);
        when(store.claimRecoverable(anyString(), any(), eq(2)))
                .thenReturn(List.of(request(1L), request(2L)))
                .thenReturn(List.of());
        when(queue.offer(1L, "m-1")).thenReturn(true);
        when(queue.offer(2L, "m-2")).thenReturn(true);

        assertThat(worker(store, queue, metrics).recover()).isEqualTo(2);
        verify(store).clearLease(List.of(1L, 2L));
        verify(metrics).recovery(2);
    }

    @Test
    void continuesUntilAShortBatch() {
        StateMachineRequestStore store = mock(StateMachineRequestStore.class);
        StateMachineDispatchQueue queue = mock(StateMachineDispatchQueue.class);
        StateMachineMetrics metrics = mock(StateMachineMetrics.class);
        when(store.claimRecoverable(anyString(), any(), eq(2)))
                .thenReturn(List.of(request(1L), request(2L)))
                .thenReturn(List.of(request(3L)))
                .thenReturn(List.of());
        when(queue.offer(anyLong(), anyString())).thenReturn(true);

        assertThat(worker(store, queue, metrics).recover()).isEqualTo(3);
        verify(store, times(2)).clearLease(any());
    }

    @Test
    void stopsWhenQueueOverflows() {
        StateMachineRequestStore store = mock(StateMachineRequestStore.class);
        StateMachineDispatchQueue queue = mock(StateMachineDispatchQueue.class);
        StateMachineMetrics metrics = mock(StateMachineMetrics.class);
        when(store.claimRecoverable(anyString(), any(), eq(2))).thenReturn(List.of(request(1L), request(2L)));
        when(queue.offer(1L, "m-1")).thenReturn(true);
        when(queue.offer(2L, "m-2")).thenReturn(false);

        assertThat(worker(store, queue, metrics).recover()).isEqualTo(1);
        verify(store, times(1)).claimRecoverable(anyString(), any(), anyInt());
    }

    @Test
    void recoverTickSwallowsFailures() {
        StateMachineRequestStore store = mock(StateMachineRequestStore.class);
        when(store.claimRecoverable(anyString(), any(), anyInt())).thenThrow(new IllegalStateException("db"));
        worker(store, mock(StateMachineDispatchQueue.class), mock(StateMachineMetrics.class))
                .recoverTick();
    }

    private static StateMachineRecoveryWorker worker(
            StateMachineRequestStore store, StateMachineDispatchQueue queue, StateMachineMetrics metrics) {
        return new StateMachineRecoveryWorker(store, queue, metrics, "pod-1", Duration.ofSeconds(30), 2);
    }

    private static StateMachineRequest request(long id) {
        return new StateMachineRequest(
                id,
                "e-" + id,
                "order-saga",
                "m-" + id,
                "START",
                null,
                StateMachineRequest.NEW,
                0,
                null,
                Instant.now(),
                Instant.now(),
                null);
    }
}
