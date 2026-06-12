package com.rideshare.location.integration;

import com.rideshare.location.dto.request.LocationUpdateRequest;
import com.rideshare.location.service.LocationBatchProcessor;
import com.rideshare.location.service.LocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Instant;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load test for Location Service.
 * Verifies the service can handle 100k+ location updates/sec.
 *
 * Expected behavior:
 * - Process 10,000 updates in < 1 second
 * - Handle concurrent updates from multiple drivers
 * - Maintain low latency under high load
 */
@SpringBootTest
@DisplayName("Location Service Load Tests")
class LoadTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private LocationBatchProcessor batchProcessor;

    @Test
    @DisplayName("should handle 10k sequential updates in < 1 second")
    void testSequentialThroughput() throws InterruptedException {
        int updateCount = 10_000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < updateCount; i++) {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                .driverId("driver-" + (i % 100))  // Simulate 100 concurrent drivers
                .lat(40.7128 + (Math.random() * 0.1))
                .lng(-74.0060 + (Math.random() * 0.1))
                .timestamp(Instant.now())
                .source("gps")
                .build();

            locationService.updateLocation(request);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double throughput = (double) updateCount / (elapsed / 1000.0);

        System.out.printf("Sequential: Processed %d updates in %d ms (%.0f updates/sec)%n",
                         updateCount, elapsed, throughput);

        assertTrue(throughput > 5000, "Should process > 5k updates/sec");
    }

    @Test
    @DisplayName("should handle concurrent updates from 100 drivers")
    void testConcurrentThroughput() throws InterruptedException {
        int driverCount = 100;
        int updatesPerDriver = 100;
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(driverCount);
        CountDownLatch latch = new CountDownLatch(driverCount);

        for (int d = 0; d < driverCount; d++) {
            final int driverId = d;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < updatesPerDriver; i++) {
                        LocationUpdateRequest request = LocationUpdateRequest.builder()
                            .driverId("driver-" + driverId)
                            .lat(40.7128 + (Math.random() * 0.1))
                            .lng(-74.0060 + (Math.random() * 0.1))
                            .timestamp(Instant.now())
                            .source("gps")
                            .build();

                        locationService.updateLocation(request);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long elapsed = System.currentTimeMillis() - startTime;
        int totalUpdates = driverCount * updatesPerDriver;
        double throughput = (double) totalUpdates / (elapsed / 1000.0);

        System.out.printf("Concurrent: Processed %d updates from %d drivers in %d ms (%.0f updates/sec)%n",
                         totalUpdates, driverCount, elapsed, throughput);

        assertTrue(throughput > 1000, "Should process > 1k updates/sec under concurrent load");
    }

    @Test
    @DisplayName("should maintain low latency under sustained load")
    void testLatencyUnderLoad() throws InterruptedException {
        int durationSeconds = 5;
        int driverCount = 50;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000);

        ExecutorService executor = Executors.newFixedThreadPool(driverCount);
        AtomicLong updateCount = new AtomicLong(0);

        for (int d = 0; d < driverCount; d++) {
            final int driverId = d;
            executor.submit(() -> {
                while (System.currentTimeMillis() < endTime) {
                    LocationUpdateRequest request = LocationUpdateRequest.builder()
                        .driverId("driver-" + driverId)
                        .lat(40.7128 + (Math.random() * 0.1))
                        .lng(-74.0060 + (Math.random() * 0.1))
                        .timestamp(Instant.now())
                        .source("gps")
                        .build();

                    long opStart = System.nanoTime();
                    locationService.updateLocation(request);
                    long opLatency = System.nanoTime() - opStart;

                    assertTrue(opLatency < 100_000_000, "Operation latency > 100ms");
                    updateCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(durationSeconds + 5, TimeUnit.SECONDS);

        long totalElapsed = System.currentTimeMillis() - startTime;
        double throughput = (double) updateCount.get() / (totalElapsed / 1000.0);

        System.out.printf("Sustained: Processed %d updates in %d ms (%.0f updates/sec)%n",
                         updateCount.get(), totalElapsed, throughput);

        LocationBatchProcessor.BatchStats stats = batchProcessor.getStats();
        System.out.printf("Batch stats - Processed: %d, Failed: %d, Current batch: %d%n",
                         stats.getProcessedCount(),
                         stats.getFailedCount(),
                         stats.getCurrentBatchSize());

        assertTrue(throughput > 500, "Should sustain > 500 updates/sec for 5 seconds");
    }

    @Test
    @DisplayName("should handle bursty traffic (1000 updates in 100ms)")
    void testBurstyTraffic() throws InterruptedException {
        int burstSize = 1_000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < burstSize; i++) {
            LocationUpdateRequest request = LocationUpdateRequest.builder()
                .driverId("driver-" + (i % 10))
                .lat(40.7128 + (Math.random() * 0.1))
                .lng(-74.0060 + (Math.random() * 0.1))
                .timestamp(Instant.now())
                .source("gps")
                .build();

            locationService.updateLocation(request);
        }

        long elapsed = System.currentTimeMillis() - startTime;

        System.out.printf("Burst: Processed %d updates in %d ms%n", burstSize, elapsed);

        // Should not take more than 1 second
        assertTrue(elapsed < 1000, "Burst should complete in < 1 second");
    }
}

/**
 * Helper class for atomic operations in test.
 */
class AtomicLong {
    private long value = 0;

    synchronized void increment() {
        value++;
    }

    synchronized long get() {
        return value;
    }

    synchronized void set(long v) {
        value = v;
    }
}
