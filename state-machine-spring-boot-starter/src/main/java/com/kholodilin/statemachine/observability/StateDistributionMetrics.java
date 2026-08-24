package com.kholodilin.statemachine.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

public final class StateDistributionMetrics {

    private final JdbcTemplate jdbcTemplate;
    private final MultiGauge gauge;

    public StateDistributionMetrics(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        this.gauge = MultiGauge.builder("state_machine_state_count").register(registry);
    }

    @Scheduled(fixedDelay = 30_000)
    public void refresh() {
        try {
            List<MultiGauge.Row<?>> rows = jdbcTemplate.query(
                    """
                    SELECT machine_type, state, COUNT(*)
                    FROM state_machine_instance
                    GROUP BY machine_type, state
                    """,
                    (rs, rowNum) -> MultiGauge.Row.of(
                            Tags.of("machineType", rs.getString(1), "state", rs.getString(2)),
                            rs.getLong(3)));
            gauge.register(rows, true);
        } catch (RuntimeException ignored) {
            // schema may not be ready, or datasource already closed
        }
    }
}
