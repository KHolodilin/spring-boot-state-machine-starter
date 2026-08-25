package com.kholodilin.statemachine.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration bound from {@code state-machine.*}. Field comments are exported to
 * Spring Boot configuration metadata for IDE completion.
 */
@ConfigurationProperties(prefix = "state-machine")
public class StateMachineProperties {

    /**
     * Master switch. When {@code false}, auto-configuration does not start workers, cache or the service.
     */
    private boolean enabled = true;

    /**
     * Identity of this process. Used as the recovery lease owner ({@code locked_by}).
     */
    private String instanceId = "local";

    /**
     * Schema management for PostgreSQL tables.
     */
    private final Persistence persistence = new Persistence();

    /**
     * In-memory Caffeine hot set. PostgreSQL remains the source of truth.
     */
    private final Cache cache = new Cache();

    /**
     * Asynchronous {@code sendAsync()} pipeline: queue, workers and recovery.
     */
    private final Async async = new Async();

    /**
     * Metrics, tracing and Actuator health toggles.
     */
    private final Observability observability = new Observability();

    /**
     * @return {@code true} if the starter should auto-configure
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled {@code false} disables the entire starter
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return process identity used as {@code locked_by} for async leases
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * @param instanceId typically {@code ${HOSTNAME}} in a multi-pod deployment
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * @return persistence / schema settings
     */
    public Persistence getPersistence() {
        return persistence;
    }

    /**
     * @return in-memory cache settings
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * @return async worker, queue and recovery settings
     */
    public Async getAsync() {
        return async;
    }

    /**
     * @return metrics, tracing and health settings
     */
    public Observability getObservability() {
        return observability;
    }

    /**
     * PostgreSQL schema management.
     */
    public static class Persistence {

        /**
         * How the starter treats {@code state-machine-schema.sql} at startup.
         */
        private final Schema schema = new Schema();

        /**
         * @return schema mode settings
         */
        public Schema getSchema() {
            return schema;
        }

        /**
         * Startup behaviour for library tables.
         */
        public static class Schema {

            /**
             * {@code create} missing tables (demo/test), {@code validate} fail-fast on mismatch
             * (production), or {@code none} when Flyway/Liquibase owns DDL.
             */
            private String mode = "validate";

            /**
             * @return {@code create}, {@code validate} or {@code none}
             */
            public String getMode() {
                return mode;
            }

            /**
             * @param mode {@code create}, {@code validate} or {@code none}
             */
            public void setMode(String mode) {
                this.mode = mode;
            }
        }
    }

    /**
     * RAM cache of instance snapshots. Eviction never writes to the database.
     */
    public static class Cache {

        /**
         * When {@code false}, lookups always go to PostgreSQL.
         */
        private boolean enabled = true;

        /**
         * Maximum number of cached instances.
         */
        private long maxSize = 100_000;

        /**
         * Drop an idle cache entry after this duration since last access.
         */
        private Duration expireAfterAccess = Duration.ofMinutes(30);

        /**
         * @return {@code true} if Caffeine cache is used
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled {@code false} installs a no-op cache
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return maximum cached instances
         */
        public long getMaxSize() {
            return maxSize;
        }

        /**
         * @param maxSize Caffeine {@code maximumSize}
         */
        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        /**
         * @return idle eviction duration
         */
        public Duration getExpireAfterAccess() {
            return expireAfterAccess;
        }

        /**
         * @param expireAfterAccess duration since last access
         */
        public void setExpireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }
    }

    /**
     * Durable async intake plus in-memory dispatch.
     */
    public static class Async {

        /**
         * Enables {@code sendAsync()}, the dispatch queue and worker threads.
         */
        private boolean enabled = true;

        /**
         * Number of worker threads and queue partitions. Events for one {@code machineId}
         * stay on one partition inside a pod.
         */
        private int workers = 8;

        /**
         * How long a {@code PROCESSING} lease stays valid before recovery may reclaim the request.
         */
        private Duration leaseDuration = Duration.ofSeconds(30);

        /**
         * Failed async requests are retried up to this count, then marked {@code DEAD}.
         */
        private int maxRetries = 5;

        /**
         * Bounded in-memory dispatch queue.
         */
        private final Queue queue = new Queue();

        /**
         * Re-offers durable requests that never reached a worker.
         */
        private final Recovery recovery = new Recovery();

        /**
         * @return {@code true} if {@code sendAsync()} is allowed
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled {@code false} makes {@code sendAsync()} throw
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return worker / partition count
         */
        public int getWorkers() {
            return workers;
        }

        /**
         * @param workers at least {@code 1} is used at runtime
         */
        public void setWorkers(int workers) {
            this.workers = workers;
        }

        /**
         * @return PROCESSING lease duration
         */
        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        /**
         * @param leaseDuration time a worker may hold a request before recovery
         */
        public void setLeaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
        }

        /**
         * @return retries before a request is marked DEAD
         */
        public int getMaxRetries() {
            return maxRetries;
        }

        /**
         * @param maxRetries failed-processing attempts
         */
        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        /**
         * @return in-memory queue settings
         */
        public Queue getQueue() {
            return queue;
        }

        /**
         * @return recovery scan settings
         */
        public Recovery getRecovery() {
            return recovery;
        }

        /**
         * Fast-path in-memory queue. Overflow is not data loss: recovery re-offers durable rows.
         */
        public static class Queue {

            /**
             * Bounded capacity across all partitions.
             */
            private int capacity = 10_000;

            /**
             * @return total queue capacity
             */
            public int getCapacity() {
                return capacity;
            }

            /**
             * @param capacity total slots across partitions
             */
            public void setCapacity(int capacity) {
                this.capacity = capacity;
            }
        }

        /**
         * Scheduled scan of {@code NEW}, {@code FAILED} and expired {@code PROCESSING} rows.
         */
        public static class Recovery {

            /**
             * When {@code false}, no recovery job is registered.
             */
            private boolean enabled = true;

            /**
             * Delay between recovery scans.
             */
            private Duration interval = Duration.ofSeconds(10);

            /**
             * Maximum number of recoverable rows claimed per scan.
             */
            private int batchSize = 500;

            /**
             * @return {@code true} if the recovery job runs
             */
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * @param enabled {@code false} skips scheduled recovery
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            /**
             * @return delay between scans
             */
            public Duration getInterval() {
                return interval;
            }

            /**
             * @param interval {@code fixedDelay} for the recovery scheduler
             */
            public void setInterval(Duration interval) {
                this.interval = interval;
            }

            /**
             * @return rows per recovery scan
             */
            public int getBatchSize() {
                return batchSize;
            }

            /**
             * @param batchSize maximum rows offered back to the queue per tick
             */
            public void setBatchSize(int batchSize) {
                this.batchSize = batchSize;
            }
        }
    }

    /**
     * Micrometer and Actuator switches. {@code machineId} and {@code eventId} must not be metric tags.
     */
    public static class Observability {

        /**
         * Transition counters, timers, cache and queue gauges.
         */
        private final Feature metrics = new Feature(true);

        /**
         * Micrometer Observation span {@code state-machine.transition}.
         */
        private final Feature tracing = new Feature(true);

        /**
         * Actuator indicator {@code stateMachine}.
         */
        private final Feature health = new Feature(true);

        /**
         * @return metrics toggle
         */
        public Feature getMetrics() {
            return metrics;
        }

        /**
         * @return tracing toggle
         */
        public Feature getTracing() {
            return tracing;
        }

        /**
         * @return health indicator toggle
         */
        public Feature getHealth() {
            return health;
        }

        /**
         * On/off flag for an observability feature.
         */
        public static class Feature {

            /**
             * Whether this feature is registered.
             */
            private boolean enabled;

            /**
             * @param enabled default for this feature
             */
            public Feature(boolean enabled) {
                this.enabled = enabled;
            }

            /**
             * @return {@code true} if the feature bean should be created
             */
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * @param enabled {@code false} skips the related auto-config bean
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }
}
