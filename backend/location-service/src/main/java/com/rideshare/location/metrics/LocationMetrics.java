package com.rideshare.location.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Prometheus metrics for Location Service.
 * Exposes key performance indicators for monitoring and alerting.
 */
@Component
public class LocationMetrics {

    private final Counter locationUpdatesTotal;
    private final Counter locationUpdatesFailed;
    private final Counter batchFlushesTotal;
    private final Counter nearbyDriverQueriesTotal;
    private final Timer nearbyDriverQueryLatency;
    private final Timer redisBatchLatency;
    private final Timer dbBatchLatency;

    public LocationMetrics(MeterRegistry meterRegistry) {
        // Counter for total location updates
        this.locationUpdatesTotal = Counter.builder("location_updates_total")
            .description("Total location updates processed")
            .register(meterRegistry);

        // Counter for failed updates
        this.locationUpdatesFailed = Counter.builder("location_updates_failed")
            .description("Total failed location updates")
            .register(meterRegistry);

        // Counter for batch flushes
        this.batchFlushesTotal = Counter.builder("location_batch_flushes_total")
            .description("Total batch flushes to Redis")
            .register(meterRegistry);

        // Counter for nearby driver queries
        this.nearbyDriverQueriesTotal = Counter.builder("location_nearby_queries_total")
            .description("Total nearby driver queries")
            .register(meterRegistry);

        // Timer for nearby driver query latency
        this.nearbyDriverQueryLatency = Timer.builder("location_nearby_query_latency")
            .description("Latency of nearby driver queries (ms)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        // Timer for Redis batch latency
        this.redisBatchLatency = Timer.builder("location_redis_batch_latency")
            .description("Latency of Redis batch writes (ms)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        // Timer for DB batch latency
        this.dbBatchLatency = Timer.builder("location_db_batch_latency")
            .description("Latency of PostgreSQL batch writes (ms)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    public void recordLocationUpdate() {
        locationUpdatesTotal.increment();
    }

    public void recordLocationUpdateFailed() {
        locationUpdatesFailed.increment();
    }

    public void recordBatchFlush() {
        batchFlushesTotal.increment();
    }

    public void recordNearbyDriverQuery(long durationMs) {
        nearbyDriverQueriesTotal.increment();
        nearbyDriverQueryLatency.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordRedisBatchLatency(long durationMs) {
        redisBatchLatency.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordDbBatchLatency(long durationMs) {
        dbBatchLatency.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
