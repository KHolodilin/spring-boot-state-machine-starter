package com.kholodilin.statemachine.async;

import java.time.Duration;

import com.kholodilin.statemachine.queue.PartitionedMemoryDispatchQueue;
import com.kholodilin.statemachine.service.DefaultStateMachineService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StateMachineWorkerPoolTest {

    @Test
    void startIsIdempotentAndStopClearsRunningFlag() {
        StateMachineWorkerPool pool = new StateMachineWorkerPool(
                new PartitionedMemoryDispatchQueue(1, 8), mock(DefaultStateMachineService.class), 1);
        pool.start();
        pool.start();
        assertThat(pool.isRunning()).isTrue();
        assertThat(pool.workerCount()).isEqualTo(1);
        pool.stop();
        assertThat(pool.isRunning()).isFalse();
    }

    @Test
    void processesOfferedIdsAndContinuesAfterFailure() {
        PartitionedMemoryDispatchQueue queue = new PartitionedMemoryDispatchQueue(1, 8);
        DefaultStateMachineService service = mock(DefaultStateMachineService.class);
        doThrow(new IllegalStateException("fail")).when(service).processRequest(2L);
        StateMachineWorkerPool pool = new StateMachineWorkerPool(queue, service, 1);
        assertThat(queue.offer(1L, "m")).isTrue();
        assertThat(queue.offer(2L, "m")).isTrue();
        pool.start();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            verify(service).processRequest(1L);
            verify(service).processRequest(2L);
        });
        pool.stop();
    }
}
