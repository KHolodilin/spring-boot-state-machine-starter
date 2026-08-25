package com.kholodilin.statemachine.queue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.capacity()).isEqualTo(2);
        assertThat(queue.partitions()).isEqualTo(1);
    }

    @Test
    void pollTimesOutOnEmptyPartition() throws InterruptedException {
        PartitionedMemoryDispatchQueue queue = new PartitionedMemoryDispatchQueue(2, 10);
        assertThat(queue.poll(0, Duration.ofMillis(20))).isNull();
    }

    @Test
    void rejectsInvalidConstructorArgs() {
        assertThatThrownBy(() -> new PartitionedMemoryDispatchQueue(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartitionedMemoryDispatchQueue(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
