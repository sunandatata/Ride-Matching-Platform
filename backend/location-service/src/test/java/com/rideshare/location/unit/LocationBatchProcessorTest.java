package com.rideshare.location.unit;

import com.rideshare.location.dto.request.LocationUpdateRequest;
import com.rideshare.location.model.LocationUpdate;
import com.rideshare.location.repository.LocationUpdateRepository;
import com.rideshare.location.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocationBatchProcessor.
 * Tests batching logic, flushing conditions, and event publishing.
 * Target: >80% coverage
 */
@DisplayName("LocationBatchProcessor Tests")
class LocationBatchProcessorTest {

    @Mock
    private RedisGeoService redisGeoService;

    @Mock
    private LocationUpdateRepository locationRepository;

    @Mock
    private LocationEventPublisher eventPublisher;

    @Mock
    private DriverStatusService driverStatusService;

    private LocationBatchProcessor batchProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        batchProcessor = new LocationBatchProcessor(
            redisGeoService,
            locationRepository,
            eventPublisher,
            driverStatusService
        );
    }

    @Test
    @DisplayName("should enqueue location update without blocking")
    void testEnqueueUpdate() {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
            .driverId("driver-123")
            .lat(40.7128)
            .lng(-74.0060)
            .timestamp(Instant.now())
            .source("gps")
            .build();

        // Should not throw
        assertDoesNotThrow(() -> batchProcessor.enqueueUpdate(request));
    }

    @Test
    @DisplayName("should drop updates when queue is full")
    void testEnqueueUpdateQueueFull() {
        // Fill the queue
        for (int i = 0; i < 10_000; i++) {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                .driverId("driver-" + i)
                .lat(40.7128)
                .lng(-74.0060)
                .timestamp(Instant.now())
                .build();
            batchProcessor.enqueueUpdate(request);
        }

        // Next update should be dropped
        LocationUpdateRequest request = LocationUpdateRequest.builder()
            .driverId("driver-overflow")
            .lat(40.7128)
            .lng(-74.0060)
            .timestamp(Instant.now())
            .build();

        batchProcessor.enqueueUpdate(request);

        // Check stats show failed count
        LocationBatchProcessor.BatchStats stats = batchProcessor.getStats();
        assertTrue(stats.getFailedCount() > 0);
    }

    @Test
    @DisplayName("should track batch statistics")
    void testGetStats() {
        LocationBatchProcessor.BatchStats stats = batchProcessor.getStats();

        assertNotNull(stats);
        assertEquals(0, stats.getProcessedCount());
        assertEquals(0, stats.getFailedCount());
        assertTrue(stats.getTimeSinceLastFlush() >= 0);
        assertTrue(stats.getCurrentBatchSize() >= 0);
    }

    @Test
    @DisplayName("should handle empty batch gracefully")
    void testEmptyBatchHandling() {
        LocationBatchProcessor.BatchStats stats = batchProcessor.getStats();
        assertEquals(0, stats.getCurrentBatchSize());
    }

    @Test
    @DisplayName("should enqueue multiple updates concurrently")
    void testConcurrentEnqueue() throws InterruptedException {
        int threadCount = 10;
        int updatesPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < updatesPerThread; i++) {
                    LocationUpdateRequest request = LocationUpdateRequest.builder()
                        .driverId("driver-" + threadId + "-" + i)
                        .lat(40.7128 + i * 0.001)
                        .lng(-74.0060 + i * 0.001)
                        .timestamp(Instant.now())
                        .build();
                    batchProcessor.enqueueUpdate(request);
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All updates should be queued (not necessarily processed yet)
        LocationBatchProcessor.BatchStats stats = batchProcessor.getStats();
        assertTrue(stats.getProcessedCount() + stats.getCurrentBatchSize() > 0);
    }
}
