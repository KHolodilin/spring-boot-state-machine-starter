package com.kholodilin.statemachine.service;

import com.kholodilin.statemachine.AsyncSubmission;
import com.kholodilin.statemachine.StateMachineEvent;
import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.StateMachineRegistry;
import com.kholodilin.statemachine.StateMachineService;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.autoconfigure.StateMachineProperties;
import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.engine.TransitionEngine;
import com.kholodilin.statemachine.exception.EventTypeMismatchException;
import com.kholodilin.statemachine.exception.OptimisticLockConflictException;
import com.kholodilin.statemachine.exception.PayloadDeserializationException;
import com.kholodilin.statemachine.observability.StateMachineMetrics;
import com.kholodilin.statemachine.persistence.JsonMaps;
import com.kholodilin.statemachine.spi.InstanceLock;
import com.kholodilin.statemachine.spi.ProcessedStateMachineEvent;
import com.kholodilin.statemachine.spi.StateMachineCache;
import com.kholodilin.statemachine.spi.StateMachineCommandPublisher;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import com.kholodilin.statemachine.spi.StateMachineEventStore;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import com.kholodilin.statemachine.spi.StateMachineStore;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class DefaultStateMachineService implements StateMachineService {

    private static final Logger log = LoggerFactory.getLogger(DefaultStateMachineService.class);

    private final StateMachineRegistry registry;
    private final TransitionEngine engine;
    private final StateMachineStore store;
    private final StateMachineEventStore eventStore;
    private final StateMachineRequestStore requestStore;
    private final StateMachineCache cache;
    private final InstanceLock instanceLock;
    private final StateMachineCommandPublisher commandPublisher;
    private final StateMachineDispatchQueue dispatchQueue;
    private final JsonMaps jsonMaps;
    private final StateMachineMetrics metrics;
    private final ObservationRegistry observationRegistry;
    private final StateMachineProperties properties;

    public DefaultStateMachineService(
            StateMachineRegistry registry,
            TransitionEngine engine,
            StateMachineStore store,
            StateMachineEventStore eventStore,
            StateMachineRequestStore requestStore,
            StateMachineCache cache,
            InstanceLock instanceLock,
            StateMachineCommandPublisher commandPublisher,
            StateMachineDispatchQueue dispatchQueue,
            JsonMaps jsonMaps,
            StateMachineMetrics metrics,
            ObservationRegistry observationRegistry,
            StateMachineProperties properties) {
        this.registry = registry;
        this.engine = engine;
        this.store = store;
        this.eventStore = eventStore;
        this.requestStore = requestStore;
        this.cache = cache;
        this.instanceLock = instanceLock;
        this.commandPublisher = commandPublisher;
        this.dispatchQueue = dispatchQueue;
        this.jsonMaps = jsonMaps;
        this.metrics = metrics;
        this.observationRegistry = observationRegistry;
        this.properties = properties;
    }

    @Override
    @Transactional
    public <E, P> TransitionResult send(String machineType, StateMachineEvent<E, P> event) {
        long started = System.nanoTime();
        TransitionResult result = observe(machineType, event, () -> processEvent(machineType, event));
        metrics.transition(result, System.nanoTime() - started);
        logResult(result, System.nanoTime() - started);
        return result;
    }

    @Override
    @Transactional
    public <E, P> AsyncSubmission sendAsync(String machineType, StateMachineEvent<E, P> event) {
        if (!properties.getAsync().isEnabled()) {
            throw new IllegalStateException("state-machine.async.enabled is false");
        }
        StateMachineDefinition<?, ?> definition = registry.getRequired(machineType);
        validateEvent(definition, event);
        String payloadJson = event.payload() == null ? null : jsonMaps.write(event.payload());
        StateMachineRequest request = new StateMachineRequest(
                null,
                event.eventId(),
                machineType,
                event.machineId(),
                event.type().toString(),
                payloadJson,
                StateMachineRequest.NEW,
                0,
                null,
                null,
                null,
                null);
        long requestId;
        try {
            requestId = requestStore.append(request);
        } catch (DuplicateKeyException ex) {
            requestId = requestStore.findByEventId(event.eventId())
                    .map(StateMachineRequest::id)
                    .orElseThrow(() -> ex);
            metrics.asyncSubmitted(machineType);
            return new AsyncSubmission(event.eventId(), machineType, event.machineId(), requestId);
        }
        long id = requestId;
        afterCommit(() -> dispatchQueue.offer(id, event.machineId()));
        metrics.asyncSubmitted(machineType);
        return new AsyncSubmission(event.eventId(), machineType, event.machineId(), requestId);
    }

    @Transactional
    public TransitionResult processRequest(long requestId) {
        Optional<StateMachineRequest> loaded = requestStore.findById(requestId);
        if (loaded.isEmpty()) {
            return null;
        }
        StateMachineRequest request = loaded.get();
        if (request.status() == StateMachineRequest.DONE || request.status() == StateMachineRequest.DEAD) {
            return null;
        }
        Instant leaseUntil = Instant.now().plus(properties.getAsync().getLeaseDuration());
        if (!requestStore.claim(requestId, properties.getInstanceId(), leaseUntil)) {
            return null;
        }
        try {
            StateMachineDefinition<?, ?> definition = registry.getRequired(request.machineType());
            Object payload = deserializePayload(definition, request);
            Enum<?> eventType = definition.eventFromName(request.eventType());
            StateMachineEvent<?, ?> event = new StateMachineEvent<>(
                    request.eventId(), request.machineId(), eventType, payload);
            TransitionResult result = send(request.machineType(), event);
            requestStore.markDone(requestId);
            metrics.asyncProcessed(request.machineType(), result.outcome().name().toLowerCase());
            return result;
        } catch (RuntimeException ex) {
            int retries = request.retryCount() + 1;
            if (retries >= properties.getAsync().getMaxRetries()) {
                requestStore.markDead(requestId);
            } else {
                requestStore.markFailed(requestId, retries);
            }
            metrics.asyncFailed(request.machineType());
            throw ex;
        }
    }

    TransitionResult processEvent(String machineType, StateMachineEvent<?, ?> event) {
        StateMachineDefinition<?, ?> definition = registry.getRequired(machineType);
        validateEvent(definition, event);
        instanceLock.acquire(machineType, event.machineId());

        Optional<ProcessedStateMachineEvent> existing = eventStore.find(event.eventId());
        if (existing.isPresent()) {
            ProcessedStateMachineEvent processed = existing.get();
            return new TransitionResult.Duplicate(
                    processed.machineType(),
                    processed.machineId(),
                    processed.eventId(),
                    processed.eventType(),
                    processed.fromState(),
                    processed.toState());
        }

        StateMachineInstance instance = loadOrCreate(definition, event.machineId());
        TransitionResult result = engine.transition(definition, instance, event);
        Timer.Sample persist = metrics.startPersist();
        try {
            persistResult(instance, result);
        } finally {
            metrics.stopPersist(persist);
        }
        return result;
    }

    private void persistResult(StateMachineInstance current, TransitionResult result) {
        if (result instanceof TransitionResult.Success success) {
            StateMachineInstance updated = new StateMachineInstance(
                    success.machineType(),
                    success.machineId(),
                    success.toState(),
                    success.context(),
                    success.version());
            if (!store.update(updated, current.version())) {
                cache.invalidate(current.machineType(), current.machineId());
                metrics.optimisticLockConflict();
                throw new OptimisticLockConflictException(
                        current.machineType(), current.machineId(), current.version());
            }
            eventStore.append(new ProcessedStateMachineEvent(
                    success.eventId(),
                    success.machineType(),
                    success.machineId(),
                    success.eventType(),
                    success.fromState(),
                    success.toState(),
                    "success",
                    Instant.now()));
            commandPublisher.publish(updated, success.commands());
            cache.put(updated);
            return;
        }
        if (result instanceof TransitionResult.Rejected rejected) {
            eventStore.append(new ProcessedStateMachineEvent(
                    rejected.eventId(),
                    rejected.machineType(),
                    rejected.machineId(),
                    rejected.eventType(),
                    rejected.state(),
                    rejected.state(),
                    "rejected",
                    Instant.now()));
        }
    }

    private StateMachineInstance loadOrCreate(StateMachineDefinition<?, ?> definition, String machineId) {
        Timer.Sample load = metrics.startLoad();
        try {
            Optional<StateMachineInstance> cached = cache.get(definition.machineType(), machineId);
            if (cached.isPresent()) {
                metrics.cacheHit();
                return cached.get();
            }
            metrics.cacheMiss();
            Optional<StateMachineInstance> stored = store.find(definition.machineType(), machineId);
            if (stored.isPresent()) {
                cache.put(stored.get());
                return stored.get();
            }
            StateMachineInstance created = store.create(new StateMachineInstance(
                    definition.machineType(),
                    machineId,
                    definition.initialState().name(),
                    Map.of(),
                    0));
            cache.put(created);
            return created;
        } finally {
            metrics.stopLoad(load);
        }
    }

    private void validateEvent(StateMachineDefinition<?, ?> definition, StateMachineEvent<?, ?> event) {
        if (!definition.eventType().isInstance(event.type())) {
            throw new EventTypeMismatchException(
                    definition.machineType(), definition.eventType(), event.type().getClass());
        }
    }

    private Object deserializePayload(StateMachineDefinition<?, ?> definition, StateMachineRequest request) {
        Class<?> payloadType = definition.findPayloadType(request.eventType())
                .orElseThrow(() -> new PayloadDeserializationException(
                        "Unknown event type " + request.eventType() + " for " + request.machineType(), null));
        return jsonMaps.read(request.payloadJson(), payloadType);
    }

    private <E, P> TransitionResult observe(String machineType, StateMachineEvent<E, P> event, java.util.function.Supplier<TransitionResult> action) {
        Observation observation = Observation.createNotStarted("state-machine.transition", observationRegistry)
                .lowCardinalityKeyValue("state.machine.type", machineType)
                .lowCardinalityKeyValue("state.event.type", String.valueOf(event.type()))
                .highCardinalityKeyValue("state.machine.id", event.machineId())
                .highCardinalityKeyValue("state.event.id", event.eventId());
        return observation.observe(() -> {
            TransitionResult result = action.get();
            observation.lowCardinalityKeyValue("state.result", result.outcome().name().toLowerCase());
            if (result instanceof TransitionResult.Success success) {
                observation.lowCardinalityKeyValue("state.from", success.fromState());
                observation.lowCardinalityKeyValue("state.to", success.toState());
            } else if (result instanceof TransitionResult.Rejected rejected) {
                observation.lowCardinalityKeyValue("state.from", rejected.state());
                observation.lowCardinalityKeyValue("state.to", rejected.state());
            }
            return result;
        });
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void logResult(TransitionResult result, long nanos) {
        String from = "";
        String to = "";
        if (result instanceof TransitionResult.Success success) {
            from = success.fromState();
            to = success.toState();
        } else if (result instanceof TransitionResult.Duplicate duplicate) {
            from = duplicate.fromState();
            to = duplicate.toState();
        } else if (result instanceof TransitionResult.Rejected rejected) {
            from = rejected.state();
            to = rejected.state();
        }
        log.atInfo()
                .addKeyValue("machineType", result.machineType())
                .addKeyValue("machineId", result.machineId())
                .addKeyValue("eventId", result.eventId())
                .addKeyValue("eventType", result.eventType())
                .addKeyValue("fromState", from)
                .addKeyValue("toState", to)
                .addKeyValue("result", result.outcome().name().toLowerCase())
                .addKeyValue("duration", nanos / 1_000_000)
                .log("state machine transition");
    }
}
