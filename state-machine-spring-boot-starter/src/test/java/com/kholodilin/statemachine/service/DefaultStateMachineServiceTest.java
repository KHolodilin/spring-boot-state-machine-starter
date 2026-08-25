package com.kholodilin.statemachine.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.kholodilin.statemachine.AsyncSubmission;
import com.kholodilin.statemachine.StateMachineEvent;
import com.kholodilin.statemachine.StateMachineInstance;
import com.kholodilin.statemachine.StateMachineRegistry;
import com.kholodilin.statemachine.TransitionOutcome;
import com.kholodilin.statemachine.TransitionResult;
import com.kholodilin.statemachine.autoconfigure.StateMachineProperties;
import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.engine.DefaultTransitionEngine;
import com.kholodilin.statemachine.exception.EventTypeMismatchException;
import com.kholodilin.statemachine.exception.OptimisticLockConflictException;
import com.kholodilin.statemachine.observability.StateMachineMetrics;
import com.kholodilin.statemachine.persistence.JsonMaps;
import com.kholodilin.statemachine.queue.PartitionedMemoryDispatchQueue;
import com.kholodilin.statemachine.spi.InstanceLock;
import com.kholodilin.statemachine.spi.ProcessedStateMachineEvent;
import com.kholodilin.statemachine.spi.StateMachineCache;
import com.kholodilin.statemachine.spi.StateMachineCommandPublisher;
import com.kholodilin.statemachine.spi.StateMachineEventStore;
import com.kholodilin.statemachine.spi.StateMachineRequest;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import com.kholodilin.statemachine.spi.StateMachineStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultStateMachineServiceTest {

    enum OrderState {
        NEW,
        PENDING,
        DONE
    }

    enum OrderEvent {
        START,
        FINISH
    }

    enum OtherEvent {
        X
    }

    private StateMachineStore store;
    private StateMachineEventStore eventStore;
    private StateMachineRequestStore requestStore;
    private StateMachineCache cache;
    private InstanceLock instanceLock;
    private StateMachineCommandPublisher commandPublisher;
    private PartitionedMemoryDispatchQueue queue;
    private StateMachineProperties properties;
    private ObservationRegistry observations;
    private final List<String> spans = new CopyOnWriteArrayList<>();
    private DefaultStateMachineService service;

    @BeforeEach
    void setUp() {
        store = mock(StateMachineStore.class);
        eventStore = mock(StateMachineEventStore.class);
        requestStore = mock(StateMachineRequestStore.class);
        cache = mock(StateMachineCache.class);
        instanceLock = mock(InstanceLock.class);
        commandPublisher = mock(StateMachineCommandPublisher.class);
        queue = new PartitionedMemoryDispatchQueue(1, 8);
        properties = new StateMachineProperties();
        observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                spans.add(context.getName());
            }
        });
        when(cache.get(anyString(), anyString())).thenReturn(Optional.empty());
        when(eventStore.find(anyString())).thenReturn(Optional.empty());
        service = newService(observations);
    }

    @Test
    void sendCreatesInstancePersistsAndRecordsSpan() {
        StateMachineInstance created = new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0);
        when(store.find("order-saga", "1")).thenReturn(Optional.empty());
        when(store.create(any())).thenReturn(created);
        when(store.update(any(), eq(0L))).thenReturn(true);

        TransitionResult result =
                service.send("order-saga", new StateMachineEvent<>("evt-1", "1", OrderEvent.START, null));

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.SUCCESS);
        assertThat(((TransitionResult.Success) result).toState()).isEqualTo("PENDING");
        assertThat(spans).contains("state-machine.transition");
        verify(eventStore).append(any(ProcessedStateMachineEvent.class));
        verify(commandPublisher).publish(any(), any());
        verify(cache, atLeastOnce()).put(any());
    }

    @Test
    void sendUsesCachedInstance() {
        StateMachineInstance cached = new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0);
        when(cache.get("order-saga", "1")).thenReturn(Optional.of(cached));
        when(store.update(any(), eq(0L))).thenReturn(true);

        assertThat(service.send("order-saga", new StateMachineEvent<>("evt-c", "1", OrderEvent.START, null))
                        .outcome())
                .isEqualTo(TransitionOutcome.SUCCESS);
        verify(store, never()).find(anyString(), anyString());
        verify(store, never()).create(any());
    }

    @Test
    void sendReturnsDuplicateFromHistory() {
        when(eventStore.find("evt-dup"))
                .thenReturn(Optional.of(new ProcessedStateMachineEvent(
                        "evt-dup", "order-saga", "1", "START", "NEW", "PENDING", "success", Instant.now())));

        TransitionResult result =
                service.send("order-saga", new StateMachineEvent<>("evt-dup", "1", OrderEvent.START, null));

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.DUPLICATE);
        verify(store, never()).update(any(), anyLong());
    }

    @Test
    void sendRejectsWhenNoTransition() {
        StateMachineInstance pending = new StateMachineInstance("order-saga", "1", "PENDING", Map.of(), 1);
        when(store.find("order-saga", "1")).thenReturn(Optional.of(pending));

        TransitionResult result =
                service.send("order-saga", new StateMachineEvent<>("evt-late", "1", OrderEvent.START, null));

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.REJECTED);
        verify(eventStore).append(any(ProcessedStateMachineEvent.class));
        verify(store, never()).update(any(), anyLong());
    }

    @Test
    void sendThrowsOnOptimisticLockConflict() {
        StateMachineInstance created = new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0);
        when(store.find("order-saga", "1")).thenReturn(Optional.of(created));
        when(store.update(any(), eq(0L))).thenReturn(false);

        assertThatThrownBy(() ->
                        service.send("order-saga", new StateMachineEvent<>("evt-ol", "1", OrderEvent.START, null)))
                .isInstanceOf(OptimisticLockConflictException.class);
        verify(cache).invalidate("order-saga", "1");
    }

    @Test
    void sendRejectsMismatchedEventType() {
        assertThatThrownBy(() -> service.send("order-saga", new StateMachineEvent<>("evt-x", "1", OtherEvent.X, null)))
                .isInstanceOf(EventTypeMismatchException.class);
    }

    @Test
    void sendAsyncAppendsAndOffers() {
        when(requestStore.append(any())).thenReturn(9L);

        AsyncSubmission submission =
                service.sendAsync("order-saga", new StateMachineEvent<>("evt-a", "1", OrderEvent.START, null));

        assertThat(submission.requestId()).isEqualTo(9L);
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    void sendAsyncDuplicateKeyReusesExistingId() {
        when(requestStore.append(any())).thenThrow(new DuplicateKeyException("dup"));
        when(requestStore.findByEventId("evt-a")).thenReturn(Optional.of(request(7L, StateMachineRequest.NEW, 0)));

        AsyncSubmission submission =
                service.sendAsync("order-saga", new StateMachineEvent<>("evt-a", "1", OrderEvent.START, null));

        assertThat(submission.requestId()).isEqualTo(7L);
        assertThat(queue.size()).isZero();
    }

    @Test
    void sendAsyncDisabledThrows() {
        properties.getAsync().setEnabled(false);
        service = newService(observations);
        assertThatThrownBy(() ->
                        service.sendAsync("order-saga", new StateMachineEvent<>("evt-a", "1", OrderEvent.START, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processRequestSkipsMissingDoneAndLostClaim() {
        when(requestStore.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.processRequest(1L)).isNull();

        when(requestStore.findById(2L)).thenReturn(Optional.of(request(2L, StateMachineRequest.DONE, 0)));
        assertThat(service.processRequest(2L)).isNull();

        when(requestStore.findById(3L)).thenReturn(Optional.of(request(3L, StateMachineRequest.DEAD, 0)));
        assertThat(service.processRequest(3L)).isNull();

        when(requestStore.findById(4L)).thenReturn(Optional.of(request(4L, StateMachineRequest.NEW, 0)));
        when(requestStore.claim(eq(4L), anyString(), any())).thenReturn(false);
        assertThat(service.processRequest(4L)).isNull();
    }

    @Test
    void processRequestMarksDoneOnSuccess() {
        when(requestStore.findById(8L)).thenReturn(Optional.of(request(8L, StateMachineRequest.NEW, 0)));
        when(requestStore.claim(eq(8L), anyString(), any())).thenReturn(true);
        StateMachineInstance created = new StateMachineInstance("order-saga", "1", "NEW", Map.of(), 0);
        when(store.find("order-saga", "1")).thenReturn(Optional.of(created));
        when(store.update(any(), eq(0L))).thenReturn(true);

        TransitionResult result = service.processRequest(8L);

        assertThat(result.outcome()).isEqualTo(TransitionOutcome.SUCCESS);
        verify(requestStore).markDone(8L);
    }

    @Test
    void processRequestMarksFailedThenDead() {
        when(requestStore.findById(5L)).thenReturn(Optional.of(request(5L, StateMachineRequest.NEW, 0)));
        when(requestStore.claim(eq(5L), anyString(), any())).thenReturn(true);
        when(store.find("order-saga", "1")).thenThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> service.processRequest(5L)).isInstanceOf(IllegalStateException.class);
        verify(requestStore).markFailed(5L, 1);

        when(requestStore.findById(6L)).thenReturn(Optional.of(request(6L, StateMachineRequest.FAILED, 4)));
        when(requestStore.claim(eq(6L), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.processRequest(6L)).isInstanceOf(IllegalStateException.class);
        verify(requestStore).markDead(6L);
    }

    private DefaultStateMachineService newService(ObservationRegistry registry) {
        StateMachineDefinition<OrderState, OrderEvent> definition = StateMachineDefinition.builder(
                        "order-saga", OrderState.class, OrderEvent.class)
                .initial(OrderState.NEW)
                .payload(OrderEvent.START, Void.class)
                .payload(OrderEvent.FINISH, Void.class)
                .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(OrderState.PENDING)
                .transition()
                .from(OrderState.PENDING)
                .event(OrderEvent.FINISH)
                .to(OrderState.DONE)
                .build();
        return new DefaultStateMachineService(
                new StateMachineRegistry.InMemory(List.of(definition)),
                new DefaultTransitionEngine(),
                store,
                eventStore,
                requestStore,
                cache,
                instanceLock,
                commandPublisher,
                queue,
                new JsonMaps(JsonMapper.builder().build()),
                new StateMachineMetrics(new SimpleMeterRegistry(), cache, queue),
                registry,
                properties);
    }

    private static StateMachineRequest request(long id, int status, int retries) {
        return new StateMachineRequest(
                id, "evt-" + id, "order-saga", "1", "START", null, status, retries, null, null, null, null);
    }
}
