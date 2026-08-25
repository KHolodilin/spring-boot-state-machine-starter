package com.kholodilin.statemachine.queue;

import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory {@link StateMachineDispatchQueue}: one {@link ArrayBlockingQueue} per worker partition.
 * {@link #offer(long, String)} hashes {@code machineId} so one instance stays on one worker inside a pod.
 */
public final class PartitionedMemoryDispatchQueue implements StateMachineDispatchQueue {

    private final List<BlockingQueue<Long>> partitions;
    private final int capacity;

    /**
     * @param partitions number of workers / queues
     * @param capacity   total slots across partitions
     */
    public PartitionedMemoryDispatchQueue(int partitions, int capacity) {
        if (partitions < 1) {
            throw new IllegalArgumentException("partitions must be >= 1");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
        List<BlockingQueue<Long>> queues = new ArrayList<>(partitions);
        int perPartition = Math.max(1, capacity / partitions);
        for (int i = 0; i < partitions; i++) {
            queues.add(new ArrayBlockingQueue<>(perPartition));
        }
        this.partitions = List.copyOf(queues);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offer(long requestId, String machineId) {
        Objects.requireNonNull(machineId, "machineId");
        int partition = Math.floorMod(machineId.hashCode(), partitions.size());
        return partitions.get(partition).offer(requestId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long poll(int partition, Duration timeout) throws InterruptedException {
        return partitions.get(partition).poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int partitions() {
        return partitions.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        int total = 0;
        for (BlockingQueue<Long> queue : partitions) {
            total += queue.size();
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int capacity() {
        return capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double pressure() {
        return (double) size() / (double) capacity;
    }
}
