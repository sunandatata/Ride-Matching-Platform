package com.rideshare.location.service;

import com.rideshare.location.dto.request.LocationUpdateRequest;
import com.rideshare.location.model.LocationUpdate;
import com.rideshare.location.repository.LocationUpdateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Critical high-throughput batching processor for location updates.
 * Batches location updates and flushes on time or size triggers.
 *
 * Design:
 * - In-memory batch accumulator with 500 message or 100ms flush window
 * - Pipelined Redis writes for geo-indexing (non-blocking)
 * - Async PostgreSQL batch writes every 5 minutes on separate thread
 * - Event publishing for each update
 *
 * Performance target: 100k+ updates/sec
 */
@Slf4j
@Service
public class LocationBatchProcessor {

    private static final int BATCH_SIZE = 500;
    private static final long BATCH_WINDOW_MS = 100;
    private static final long DB_FLUSH_INTERVAL_MS = 300_000;  // 5 minutes

    private final RedisGeoService redisGeoService;
    private final LocationUpdateRepository locationRepository;
    private final LocationEventPublisher eventPublisher;
    private final DriverStatusService driverStatusService;

    @Value("${location.batch.size:500}")
    private int configuredBatchSize;

    @Value("${location.batch.window.ms:100}")
    private long configuredBatchWindowMs;

    private final BlockingQueue<LocationUpdateRequest> inputQueue;
    private final List<LocationUpdate> batch = Collections.synchronizedList(new ArrayList<>());
    private volatile long lastFlushTime = System.currentTimeMillis();
    private volatile long lastDbFlushTime = System.currentTimeMillis();

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    private final ScheduledExecutorService batchScheduler;
    private final ExecutorService dbWriter;

    public LocationBatchProcessor(
            RedisGeoService redisGeoService,
            LocationUpdateRepository locationRepository,
            LocationEventPublisher eventPublisher,
            DriverStatusService driverStatusService) {

        this.redisGeoService = redisGeoService;
        this.locationRepository = locationRepository;
        this.eventPublisher = eventPublisher;
        this.driverStatusService = driverStatusService;

        // Input queue for incoming location updates
        this.inputQueue = new LinkedBlockingQueue<>(10_000);

        // Batch flush scheduler - runs every 50ms to check flush conditions
        this.batchScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "LocationBatchFlushScheduler");
            t.setDaemon(true);
            return t;
        });

        // Async DB writer thread - runs batches every 5 minutes
        this.dbWriter = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LocationDbWriter");
            t.setDaemon(true);
            return t;
        });

        startBatchProcessing();
    }

    /**
     * Enqueue a location update for batching.
     * Non-blocking, drops updates if queue is full (circuit breaker).
     *
     * @param request Location update request
     */
    public void enqueueUpdate(LocationUpdateRequest request) {
        if (!inputQueue.offer(request)) {
            failedCount.incrementAndGet();
            log.warn("Input queue full, dropped location update for driver {}", request.getDriverId());
        }
    }

    /**
     * Get current batch stats (monitoring/metrics).
     */
    public BatchStats getStats() {
        return BatchStats.builder()
            .processedCount(processedCount.get())
            .failedCount(failedCount.get())
            .currentBatchSize(batch.size())
            .timeSinceLastFlush(System.currentTimeMillis() - lastFlushTime)
            .inputQueueSize(inputQueue.size())
            .build();
    }

    /**
     * Start the batch processing loop.
     * Runs continuously, pulling from input queue and flushing on time/size.
     */
    private void startBatchProcessing() {
        // Main processing thread
        Thread processorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Non-blocking poll with timeout
                    LocationUpdateRequest request = inputQueue.poll(10, TimeUnit.MILLISECONDS);

                    if (request != null) {
                        // Convert and add to batch
                        LocationUpdate update = convertRequestToEntity(request);
                        batch.add(update);

                        // Publish event immediately (for Matching Engine, etc.)
                        try {
                            eventPublisher.publishLocationChanged(update);
                        } catch (Exception e) {
                            log.error("Failed to publish location event for driver {}",
                                    request.getDriverId(), e);
                        }

                        // Update driver status
                        driverStatusService.markOnline(request.getDriverId());
                    }

                    // Check flush conditions
                    long currentTime = System.currentTimeMillis();
                    if (batch.size() >= configuredBatchSize ||
                        (currentTime - lastFlushTime) > configuredBatchWindowMs) {
                        flushBatch();
                    }

                    // Check DB flush interval
                    if ((currentTime - lastDbFlushTime) > DB_FLUSH_INTERVAL_MS) {
                        scheduleDbWrite();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Batch processor interrupted");
                } catch (Exception e) {
                    log.error("Error in batch processing loop", e);
                }
            }
        }, "LocationBatchProcessor");

        processorThread.setDaemon(false);
        processorThread.start();

        // Schedule periodic flush checks
        batchScheduler.scheduleAtFixedRate(
            this::checkAndFlush,
            configuredBatchWindowMs,
            configuredBatchWindowMs,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Flush current batch to Redis Geo (pipelined).
     * Non-blocking operation.
     */
    private void flushBatch() {
        if (batch.isEmpty()) {
            return;
        }

        try {
            List<LocationUpdate> updates = new ArrayList<>(batch);
            long startTime = System.currentTimeMillis();

            // Pipelined Redis write
            redisGeoService.addLocationsBatch(updates);

            long elapsed = System.currentTimeMillis() - startTime;
            processedCount.addAndGet(updates.size());

            log.info("Flushed {} location updates to Redis in {}ms",
                    updates.size(), elapsed);

            // Clear batch
            batch.clear();
            lastFlushTime = System.currentTimeMillis();

        } catch (Exception e) {
            log.error("Failed to flush batch to Redis", e);
            failedCount.addAndGet(batch.size());
        }
    }

    /**
     * Check and flush batch if conditions met.
     */
    private void checkAndFlush() {
        if (batch.size() >= configuredBatchSize ||
            (System.currentTimeMillis() - lastFlushTime) > configuredBatchWindowMs) {
            flushBatch();
        }
    }

    /**
     * Schedule async write of batched updates to PostgreSQL.
     * Runs asynchronously to avoid blocking incoming updates.
     */
    private void scheduleDbWrite() {
        if (batch.isEmpty()) {
            return;
        }

        List<LocationUpdate> toBeSaved = new ArrayList<>(batch);

        dbWriter.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                locationRepository.saveAll(toBeSaved);
                long elapsed = System.currentTimeMillis() - startTime;

                log.info("Persisted {} locations to PostgreSQL in {}ms",
                        toBeSaved.size(), elapsed);

                lastDbFlushTime = System.currentTimeMillis();

            } catch (Exception e) {
                log.error("Failed to persist locations to PostgreSQL", e);
            }
        });
    }

    /**
     * Convert request DTO to entity for persistence.
     */
    private LocationUpdate convertRequestToEntity(LocationUpdateRequest request) {
        return LocationUpdate.builder()
            .driverId(request.getDriverId())
            .latitude(BigDecimal.valueOf(request.getLat()))
            .longitude(BigDecimal.valueOf(request.getLng()))
            .heading(request.getHeading())
            .speed(request.getSpeed())
            .accuracy(request.getAccuracy())
            .timestamp(request.getTimestamp())
            .source(request.getSource() != null ? request.getSource() : "gps")
            .build();
    }

    /**
     * Batch statistics for monitoring.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    public static class BatchStats {
        private long processedCount;
        private long failedCount;
        private int currentBatchSize;
        private long timeSinceLastFlush;
        private int inputQueueSize;
    }
}
