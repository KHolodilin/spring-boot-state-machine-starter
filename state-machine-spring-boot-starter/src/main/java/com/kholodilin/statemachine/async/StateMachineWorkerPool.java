package com.kholodilin.statemachine.async;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kholodilin.statemachine.service.DefaultStateMachineService;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * One daemon thread per queue partition. Must not block on remote I/O; transitions emit commands instead.
 */
public final class StateMachineWorkerPool implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StateMachineWorkerPool.class);

    private final StateMachineDispatchQueue queue;
    private final DefaultStateMachineService service;
    private final int workers;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> threads = new ArrayList<>();

    /**
     * @param queue   partitioned dispatch
     * @param service processes request ids
     * @param workers partition count
     */
    public StateMachineWorkerPool(StateMachineDispatchQueue queue, DefaultStateMachineService service, int workers) {
        this.queue = queue;
        this.service = service;
        this.workers = workers;
    }

    /**
     * Starts one thread per partition.
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        for (int i = 0; i < workers; i++) {
            int partition = i;
            Thread thread = new Thread(() -> runWorker(partition), "state-machine-worker-" + partition);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
    }

    private void runWorker(int partition) {
        while (running.get()) {
            try {
                Long requestId = queue.poll(partition, Duration.ofSeconds(1));
                if (requestId != null) {
                    try {
                        service.processRequest(requestId);
                    } catch (RuntimeException ex) {
                        log.warn("Failed to process async request {}", requestId, ex);
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Interrupts worker threads so {@link StateMachineDispatchQueue#poll} returns.
     */
    @Override
    public void stop() {
        running.set(false);
        for (Thread thread : threads) {
            thread.interrupt();
        }
        threads.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return configured partition / thread count
     */
    public int workerCount() {
        return workers;
    }
}
