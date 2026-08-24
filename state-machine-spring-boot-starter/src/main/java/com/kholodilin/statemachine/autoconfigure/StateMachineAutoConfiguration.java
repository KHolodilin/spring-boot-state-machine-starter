package com.kholodilin.statemachine.autoconfigure;

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
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(StateMachineProperties.class)
@ConditionalOnProperty(prefix = "state-machine", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
public class StateMachineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    StateMachineRegistry stateMachineRegistry(List<StateMachineDefinition<?, ?>> definitions) {
        return new StateMachineRegistry.InMemory(definitions);
    }

    @Bean
    @ConditionalOnMissingBean
    TransitionEngine transitionEngine() {
        return new DefaultTransitionEngine();
    }

    @Bean(name = "stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineSchemaManager stateMachineSchemaManager(DataSource dataSource, StateMachineProperties properties) {
        SchemaMode mode = SchemaMode.valueOf(properties.getPersistence().getSchema().getMode().toUpperCase(Locale.ROOT));
        StateMachineSchemaManager manager = new StateMachineSchemaManager(dataSource, mode);
        manager.apply();
        return manager;
    }

    @Bean
    @ConditionalOnMissingBean
    JsonMaps jsonMaps(ObjectProvider<JsonMapper> mappers) {
        return new JsonMaps(mappers.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    @Bean
    @DependsOn("stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineStore stateMachineStore(JdbcTemplate jdbcTemplate, JsonMaps jsonMaps) {
        return new JdbcStateMachineStore(jdbcTemplate, jsonMaps);
    }

    @Bean
    @DependsOn("stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineEventStore stateMachineEventStore(JdbcTemplate jdbcTemplate) {
        return new JdbcStateMachineEventStore(jdbcTemplate);
    }

    @Bean
    @DependsOn("stateMachineSchemaManager")
    @ConditionalOnMissingBean
    StateMachineRequestStore stateMachineRequestStore(JdbcTemplate jdbcTemplate) {
        return new JdbcStateMachineRequestStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    InstanceLock instanceLock(JdbcTemplate jdbcTemplate) {
        return new PostgresInstanceLock(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    StateMachineCache stateMachineCache(StateMachineProperties properties) {
        if (!properties.getCache().isEnabled()) {
            return new NoOpStateMachineCache();
        }
        return new CaffeineStateMachineCache(
                properties.getCache().getMaxSize(),
                properties.getCache().getExpireAfterAccess());
    }

    @Bean
    @ConditionalOnMissingBean
    StateMachineDispatchQueue stateMachineDispatchQueue(StateMachineProperties properties) {
        int workers = Math.max(1, properties.getAsync().getWorkers());
        return new PartitionedMemoryDispatchQueue(workers, properties.getAsync().getQueue().getCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    StateMachineCommandPublisher stateMachineCommandPublisher() {
        return new LoggingCommandPublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    StateMachineMetrics stateMachineMetrics(
            ObjectProvider<MeterRegistry> registries,
            StateMachineCache cache,
            StateMachineDispatchQueue queue) {
        MeterRegistry registry = registries.getIfAvailable(SimpleMeterRegistry::new);
        return new StateMachineMetrics(registry, cache, queue);
    }

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
                observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                properties);
    }

    @Bean
    @ConditionalOnMissingBean(StateMachineService.class)
    StateMachineService stateMachineService(DefaultStateMachineService service) {
        return service;
    }

    @Bean
    @ConditionalOnProperty(prefix = "state-machine.async", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    StateMachineWorkerPool stateMachineWorkerPool(
            StateMachineDispatchQueue queue,
            DefaultStateMachineService service,
            StateMachineProperties properties) {
        return new StateMachineWorkerPool(queue, service, properties.getAsync().getWorkers());
    }

    @Bean
    @ConditionalOnProperty(prefix = "state-machine.async.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    StateMachineRecoveryWorker stateMachineRecoveryWorker(
            StateMachineRequestStore requestStore,
            StateMachineDispatchQueue queue,
            StateMachineMetrics metrics,
            StateMachineProperties properties) {
        return new StateMachineRecoveryWorker(
                requestStore, queue, metrics, properties.getAsync().getRecovery().getBatchSize());
    }

    @Bean
    @ConditionalOnProperty(prefix = "state-machine.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    StateDistributionMetrics stateDistributionMetrics(JdbcTemplate jdbcTemplate, ObjectProvider<MeterRegistry> registries) {
        return new StateDistributionMetrics(jdbcTemplate, registries.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "state-machine.observability.health", name = "enabled", havingValue = "true", matchIfMissing = true)
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
}
