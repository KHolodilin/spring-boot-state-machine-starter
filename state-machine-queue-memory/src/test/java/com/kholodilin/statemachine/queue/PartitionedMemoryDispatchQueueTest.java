package com.kholodilin.statemachine.queue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PartitionedMemoryDispatchQueueTest {

    @Test
    void routesSameMachineIdToSamePartition() throws InterruptedException {
        PartitionedMemoryDispatchQueue queue = new PartitionedMemoryDispatchQueue(4, 100);
        assertThat(queue.offer(1L, "order-1")).isTrue();
        assertThat(queue.offer(2L, "order-1")).isTrue();
        int seen = 0;
        for (int i = 0; i < 4; i++) {
            Long first = queue.poll(i, Duration.ofMillis(10));
            if (first != null) {
                seen++;
                assertThat(queue.poll(i, Duration.ofMillis(10))).isEqualTo(2L);
            }
        }
        assertThat(seen).isEqualTo(1);
    }

    @Test
    void overflowDoesNotBlock() {
        PartitionedMemoryDispatchQueue queue = new PartitionedMemoryDispatchQueue(1, 2);
        assertThat(queue.offer(1L, "m")).isTrue();
        assertThat(queue.offer(2L, "m")).isTrue();
        assertThat(queue.offer(3L, "m")).isFalse();
        assertThat(queue.pressure()).isEqualTo(1.0);
    }
}
