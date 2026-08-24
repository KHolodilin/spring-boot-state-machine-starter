package com.kholodilin.statemachine.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "state-machine")
public class StateMachineProperties {

    private boolean enabled = true;
    private String instanceId = "local";
    private final Persistence persistence = new Persistence();
    private final Cache cache = new Cache();
    private final Async async = new Async();
    private final Observability observability = new Observability();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public Cache getCache() {
        return cache;
    }

    public Async getAsync() {
        return async;
    }

    public Observability getObservability() {
        return observability;
    }

    public static class Persistence {
        private final Schema schema = new Schema();

        public Schema getSchema() {
            return schema;
        }

        public static class Schema {
            /**
             * create, validate or none.
             */
            private String mode = "validate";

            public String getMode() {
                return mode;
            }

            public void setMode(String mode) {
                this.mode = mode;
            }
        }
    }

    public static class Cache {
        private boolean enabled = true;
        private long maxSize = 100_000;
        private Duration expireAfterAccess = Duration.ofMinutes(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getExpireAfterAccess() {
            return expireAfterAccess;
        }

        public void setExpireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
        }
    }

    public static class Async {
        private boolean enabled = true;
        private int workers = 8;
        private Duration leaseDuration = Duration.ofSeconds(30);
        private int maxRetries = 5;
        private final Queue queue = new Queue();
        private final Recovery recovery = new Recovery();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWorkers() {
            return workers;
        }

        public void setWorkers(int workers) {
            this.workers = workers;
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public void setLeaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Queue getQueue() {
            return queue;
        }

        public Recovery getRecovery() {
            return recovery;
        }

        public static class Queue {
            private int capacity = 10_000;

            public int getCapacity() {
                return capacity;
            }

            public void setCapacity(int capacity) {
                this.capacity = capacity;
            }
        }

        public static class Recovery {
            private boolean enabled = true;
            private Duration interval = Duration.ofSeconds(10);
            private int batchSize = 500;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Duration getInterval() {
                return interval;
            }

            public void setInterval(Duration interval) {
                this.interval = interval;
            }

            public int getBatchSize() {
                return batchSize;
            }

            public void setBatchSize(int batchSize) {
                this.batchSize = batchSize;
            }
        }
    }

    public static class Observability {
        private final Feature metrics = new Feature(true);
        private final Feature tracing = new Feature(true);
        private final Feature health = new Feature(true);

        public Feature getMetrics() {
            return metrics;
        }

        public Feature getTracing() {
            return tracing;
        }

        public Feature getHealth() {
            return health;
        }

        public static class Feature {
            private boolean enabled;

            public Feature(boolean enabled) {
                this.enabled = enabled;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }
}
