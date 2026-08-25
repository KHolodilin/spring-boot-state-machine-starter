package com.kholodilin.statemachine.async;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.kholodilin.statemachine.observability.StateMachineMetrics;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Re-offers {@code NEW}, {@code FAILED} and expired {@code PROCESSING} rows to the in-memory queue.
 * Claims batches with {@code FOR UPDATE SKIP LOCKED}, clears the lease, then {@code offer}s.
 * Does not run the transition engine itself.
 */
public final class StateMachineRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(StateMachineRecoveryWorker.class);

    private final StateMachineRequestStore requestStore;
    private final StateMachineDispatchQueue queue;
    private final StateMachineMetrics metrics;
    private final String instanceId;
    private final Duration leaseDuration;
    private final int batchSize;

    /**
     * @param requestStore  durable requests
     * @param queue         in-memory fast path
     * @param metrics       recovery counter
     * @param instanceId    this process {@code instance-id}
     * @param leaseDuration claim lease while selecting a batch
     * @param batchSize     rows per {@code SKIP LOCKED} batch
     */
    public StateMachineRecoveryWorker(
            StateMachineRequestStore requestStore,
            StateMachineDispatchQueue queue,
            StateMachineMetrics metrics,
            String instanceId,
            Duration leaseDuration,
            int batchSize) {
        this.requestStore = requestStore;
        this.queue = queue;
        this.metrics = metrics;
        this.instanceId = instanceId;
        this.leaseDuration = leaseDuration;
        this.batchSize = batchSize;
    }

    /**
     * Scheduled by {@code state-machine.async.recovery.interval}.
     */
    @Scheduled(fixedDelayString = "${state-machine.async.recovery.interval:10s}")
    public void recoverTick() {
        try {
            recover();
        } catch (RuntimeException ex) {
            log.debug("Recovery scan skipped: {}", ex.getMessage());
        }
    }

    /**
     * Claims recoverable rows in batches until none remain or the in-memory queue overflows.
     *
     * @return number of request ids offered to the dispatch queue
     */
    public int recover() {
        int offered = 0;
        while (true) {
            Instant lockedUntil = Instant.now().plus(leaseDuration);
            List<StateMachineRequest> batch = requestStore.claimRecoverable(instanceId, lockedUntil, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            List<Long> ids = batch.stream().map(StateMachineRequest::id).toList();
            requestStore.clearLease(ids);
            boolean overflow = false;
            for (StateMachineRequest request : batch) {
                if (queue.offer(request.id(), request.machineId())) {
                    offered++;
                } else {
                    overflow = true;
                }
            }
            if (overflow || batch.size() < batchSize) {
                break;
            }
        }
        if (offered > 0) {
            metrics.recovery(offered);
            log.debug("Recovered {} async requests into the dispatch queue", offered);
        }
        return offered;
    }
}
