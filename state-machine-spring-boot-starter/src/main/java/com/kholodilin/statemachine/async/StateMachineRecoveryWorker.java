package com.kholodilin.statemachine.async;

import com.kholodilin.statemachine.observability.StateMachineMetrics;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

public final class StateMachineRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(StateMachineRecoveryWorker.class);

    private final StateMachineRequestStore requestStore;
    private final StateMachineDispatchQueue queue;
    private final StateMachineMetrics metrics;
    private final int batchSize;

    public StateMachineRecoveryWorker(
            StateMachineRequestStore requestStore,
            StateMachineDispatchQueue queue,
            StateMachineMetrics metrics,
            int batchSize) {
        this.requestStore = requestStore;
        this.queue = queue;
        this.metrics = metrics;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${state-machine.async.recovery.interval:10s}")
    public void recover() {
        try {
            List<StateMachineRequest> recoverable = requestStore.findRecoverable(batchSize);
            if (recoverable.isEmpty()) {
                return;
            }
            int offered = 0;
            for (StateMachineRequest request : recoverable) {
                if (queue.offer(request.id(), request.machineId())) {
                    offered++;
                }
            }
            metrics.recovery(offered);
            log.debug("Recovered {} async requests into the dispatch queue", offered);
        } catch (RuntimeException ex) {
            log.debug("Recovery scan skipped: {}", ex.getMessage());
        }
    }
}
