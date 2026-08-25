package com.kholodilin.statemachine.autoconfigure;

import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

import com.kholodilin.statemachine.StateMachineRegistry;
import com.kholodilin.statemachine.StateMachineService;
import com.kholodilin.statemachine.async.StateMachineRecoveryWorker;
import com.kholodilin.statemachine.async.StateMachineWorkerPool;
import com.kholodilin.statemachine.cache.CaffeineStateMachineCache;
import com.kholodilin.statemachine.cache.NoOpStateMachineCache;
import com.kholodilin.statemachine.definition.StateMachineDefinition;
import com.kholodilin.statemachine.engine.DefaultTransitionEngine;
import com.kholodilin.statemachine.engine.TransitionEngine;
import com.kholodilin.statemachine.observability.StateDistributionMetrics;
import com.kholodilin.statemachine.observability.StateMachineHealthIndicator;
import com.kholodilin.statemachine.observability.StateMachineMetrics;
import com.kholodilin.statemachine.persistence.JdbcStateMachineEventStore;
import com.kholodilin.statemachine.persistence.JdbcStateMachineRequestStore;
import com.kholodilin.statemachine.persistence.JdbcStateMachineStore;
import com.kholodilin.statemachine.persistence.JsonMaps;
import com.kholodilin.statemachine.persistence.PostgresInstanceLock;
import com.kholodilin.statemachine.persistence.SchemaMode;
import com.kholodilin.statemachine.persistence.StateMachineSchemaManager;
import com.kholodilin.statemachine.queue.PartitionedMemoryDispatchQueue;
import com.kholodilin.statemachine.service.DefaultStateMachineService;
import com.kholodilin.statemachine.spi.InstanceLock;
import com.kholodilin.statemachine.spi.LoggingCommandPublisher;
import com.kholodilin.statemachine.spi.StateMachineCache;
import com.kholodilin.statemachine.spi.StateMachineCommandPublisher;
import com.kholodilin.statemachine.spi.StateMachineDispatchQueue;
import com.kholodilin.statemachine.spi.StateMachineEventStore;
import com.kholodilin.statemachine.spi.StateMachineRequestStore;
import com.kholodilin.statemachine.spi.StateMachineStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires definitions, JDBC stores, cache, async workers, metrics and health when {@code state-machine.enabled} is true.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(StateMachineProperties.class)
@ConditionalOnProperty(prefix = "state-machine", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
public class StateMachineAutoConfiguration {

    /**
     * Registry of all {@link StateMachineDefinition} beans. Duplicate {@code machineType} fails startup.
     *
     * @param definitions application beans
     * @return in-memory registry
     */
    @Bean
    @ConditionalOnMissingBean
    StateMachineRegistry stateMachineRegistry(List<StateMachineDefinition<?, ?>> definitions) {
        return new StateMachineRegistry.InMemory(definitions);
    }

    /**
     * @return pure transition engine
     */
    @Bean
    @ConditionalOnMissingBean
    TransitionEngine transitionEngine() {
        return new DefaultTransitionEngine();
    }

    /**
     * Applies schema {@code create}/{@code validate}/{@code none} before stores are used.
     *
     * @param dataSource application DataSource
     * @param properties schema mode
     * @return manager after {@link StateMachineSchemaManager#apply()}
     */
    @Bean(name = "stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineSchemaManager stateMachineSchemaManager(DataSource dataSource, StateMachineProperties properties) {
        SchemaMode mode = SchemaMode.valueOf(
                properties.getPersistence().getSchema().getMode().toUpperCase(Locale.ROOT));
        StateMachineSchemaManager manager = new StateMachineSchemaManager(dataSource, mode);
        manager.apply();
        return manager;
    }

    /**
     * @param mappers optional application {@link JsonMapper}
     * @return JSON helpers for context and payloads
     */
    @Bean
    @ConditionalOnMissingBean
    JsonMaps jsonMaps(ObjectProvider<JsonMapper> mappers) {
        return new JsonMaps(mappers.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    /**
     * @param jdbcTemplate Spring JDBC
     * @param jsonMaps     context codec
     * @return instance store
     */
    @Bean
    @DependsOn("stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineStore stateMachineStore(JdbcTemplate jdbcTemplate, JsonMaps jsonMaps) {
        return new JdbcStateMachineStore(jdbcTemplate, jsonMaps);
    }

    /**
     * @param jdbcTemplate Spring JDBC
     * @return event history store
     */
    @Bean
    @DependsOn("stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineEventStore stateMachineEventStore(JdbcTemplate jdbcTemplate) {
        return new JdbcStateMachineEventStore(jdbcTemplate);
    }

    /**
     * @param jdbcTemplate Spring JDBC
     * @return async request store
     */
    @Bean
    @DependsOn("stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineRequestStore stateMachineRequestStore(JdbcTemplate jdbcTemplate) {
        return new JdbcStateMachineRequestStore(jdbcTemplate);
    }

    /**
     * @param jdbcTemplate Spring JDBC
     * @return PostgreSQL advisory lock
     */
    @Bean
    @ConditionalOnMissingBean
    InstanceLock instanceLock(JdbcTemplate jdbcTemplate) {
        return new PostgresInstanceLock(jdbcTemplate);
    }

    /**
     * @param properties cache size and TTL
     * @return Caffeine cache or no-op when disabled
     */
    @Bean
    @ConditionalOnMissingBean
    StateMachineCache stateMachineCache(StateMachineProperties properties) {
        if (!properties.getCache().isEnabled()) {
            return new NoOpStateMachineCache();
        }
        return new CaffeineStateMachineCache(
                properties.getCache().getMaxSize(), properties.getCache().getExpireAfterAccess());
    }

    /**
     * @param properties worker count and queue capacity
     * @return partitioned in-memory queue
     */
    @Bean
    @ConditionalOnMissingBean
    StateMachineDispatchQueue stateMachineDispatchQueue(StateMachineProperties properties) {
        int workers = Math.max(1, properties.getAsync().getWorkers());
        return new PartitionedMemoryDispatchQueue(
                workers, properties.getAsync().getQueue().getCapacity());
    }

    /**
     * Default publisher logs commands. Replace with a transactional Outbox bean in production.
     *
     * @return logging publisher
     */
    @Bean
    @ConditionalOnMissingBean
    StateMachineCommandPublisher stateMachineCommandPublisher() {
        return new LoggingCommandPublisher();
    }

    /**
     * @param registries Micrometer registry if present
     * @param cache      for size/eviction gauges
     * @param queue      for size/pressure gauges
     * @return transition and async metrics
     */
    @Bean
    @ConditionalOnMissingBean
    StateMachineMetrics stateMachineMetrics(
            ObjectProvider<MeterRegistry> registries, StateMachineCache cache, StateMachineDispatchQueue queue) {
        MeterRegistry registry = registries.getIfAvailable(SimpleMeterRegistry::new);
        return new StateMachineMetrics(registry, cache, queue);
    }

    /**
     * Core {@code send}/{@code sendAsync} implementation.
     *
     * @return transactional service
     */
    @Bean
    @ConditionalOnMissingBean
    DefaultStateMachineService defaultStateMachineService(
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
            ObjectProvider<ObservationRegistry> observations,
            StateMachineProperties properties) {
        return new DefaultStateMachineService(
                registry,
                engine,
                store,
                eventStore,
                requestStore,
                cache,
                instanceLock,
                commandPublisher,
                dispatchQueue,
                jsonMaps,
                metrics,
                tracingRegistry(observations, properties),
                properties);
    }

    /**
     * @param service implementation bean
     * @return public {@link StateMachineService} alias
     */
    @Bean
    @ConditionalOnMissingBean(StateMachineService.class)
    StateMachineService stateMachineService(DefaultStateMachineService service) {
        return service;
    }

    /**
     * One platform thread per queue partition.
     *
     * @return worker pool lifecycle
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "state-machine.async",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    StateMachineWorkerPool stateMachineWorkerPool(
            StateMachineDispatchQueue queue, DefaultStateMachineService service, StateMachineProperties properties) {
        return new StateMachineWorkerPool(queue, service, properties.getAsync().getWorkers());
    }

    /**
     * Re-offers durable requests that never reached a worker.
     *
     * @return scheduled recovery job
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "state-machine.async.recovery",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    StateMachineRecoveryWorker stateMachineRecoveryWorker(
            StateMachineRequestStore requestStore,
            StateMachineDispatchQueue queue,
            StateMachineMetrics metrics,
            StateMachineProperties properties) {
        return new StateMachineRecoveryWorker(
                requestStore,
                queue,
                metrics,
                properties.getInstanceId(),
                properties.getAsync().getLeaseDuration(),
                properties.getAsync().getRecovery().getBatchSize());
    }

    /**
     * Periodic {@code state_machine_state_count} gauge by machine type and state.
     *
     * @return distribution metrics
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "state-machine.observability.metrics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    StateDistributionMetrics stateDistributionMetrics(
            JdbcTemplate jdbcTemplate, ObjectProvider<MeterRegistry> registries) {
        return new StateDistributionMetrics(jdbcTemplate, registries.getIfAvailable(SimpleMeterRegistry::new));
    }

    /**
     * Actuator indicator {@code stateMachine}. Queue overflow is not {@code DOWN} by itself.
     *
     * @return health contributor
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(
            prefix = "state-machine.observability.health",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "stateMachineHealthIndicator")
    StateMachineHealthIndicator stateMachineHealthIndicator(
            StateMachineCache cache,
            StateMachineDispatchQueue queue,
            ObjectProvider<StateMachineWorkerPool> workers,
            StateMachineRequestStore requestStore,
            StateMachineProperties properties) {
        return new StateMachineHealthIndicator(
                cache,
                queue,
                workers.getIfAvailable(),
                requestStore,
                properties.getAsync().getRecovery().isEnabled());
    }

    /**
     * {@link ObservationRegistry#NOOP} when {@code state-machine.observability.tracing.enabled} is {@code false}.
     */
    static ObservationRegistry tracingRegistry(
            ObjectProvider<ObservationRegistry> observations, StateMachineProperties properties) {
        if (!properties.getObservability().getTracing().isEnabled()) {
            return ObservationRegistry.NOOP;
        }
        return observations.getIfAvailable(() -> ObservationRegistry.NOOP);
    }
}
