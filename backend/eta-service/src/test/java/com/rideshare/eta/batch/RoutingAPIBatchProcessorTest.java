package com.rideshare.eta.batch;

import com.rideshare.eta.cache.RouteCacheService;
import com.rideshare.eta.dto.RouteData;
import com.rideshare.eta.dto.RoutingAPIResponse;
import com.rideshare.eta.routing.RoutingAPIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoutingAPIBatchProcessor.
 * Tests request batching, deduplication, and API call reduction.
 */
@ExtendWith(MockitoExtension.class)
class RoutingAPIBatchProcessorTest {

    @Mock
    private RoutingAPIClient routingAPIClient;

    @Mock
    private RouteCacheService cacheService;

    private RoutingAPIBatchProcessor batchProcessor;

    @BeforeEach
    void setUp() {
        batchProcessor = new RoutingAPIBatchProcessor(routingAPIClient, cacheService);
    }

    @Test
    void should_batch_multiple_requests() throws Exception {
        // Given
        RoutingAPIResponse apiResponse = new RoutingAPIResponse(720, 3200.0, true);
        when(routingAPIClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Optional.of(apiResponse));

        BatchRequest req1 = new BatchRequest("key1", 40.0, -74.0, 41.0, -73.0,
            new CompletableFuture<>());
        BatchRequest req2 = new BatchRequest("key2", 40.1, -74.1, 41.1, -73.1,
            new CompletableFuture<>());

        // When
        CompletableFuture<BatchResult> future1 = batchProcessor.addRequest(req1);
        CompletableFuture<BatchResult> future2 = batchProcessor.addRequest(req2);

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertEquals(1, batchProcessor.getAPICallCount());  // Only 1 API call for 2 requests
    }

    @Test
    void should_deduplicate_identical_routes() throws Exception {
        // Given
        RoutingAPIResponse apiResponse = new RoutingAPIResponse(720, 3200.0, true);
        when(routingAPIClient.getRoute(40.0, -74.0, 41.0, -73.0))
            .thenReturn(Optional.of(apiResponse));

        BatchRequest req1 = new BatchRequest("key", 40.0, -74.0, 41.0, -73.0,
            new CompletableFuture<>());
        BatchRequest req2 = new BatchRequest("key", 40.0, -74.0, 41.0, -73.0,
            new CompletableFuture<>());

        // When
        CompletableFuture<BatchResult> future1 = batchProcessor.addRequest(req1);
        CompletableFuture<BatchResult> future2 = batchProcessor.addRequest(req2);

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        verify(routingAPIClient, times(1)).getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void should_calculate_API_reduction_ratio() throws Exception {
        // Given
        RoutingAPIResponse apiResponse = new RoutingAPIResponse(720, 3200.0, true);
        when(routingAPIClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Optional.of(apiResponse));

        // Add 10 requests for 3 unique routes
        for (int i = 0; i < 10; i++) {
            BatchRequest req = new BatchRequest("key" + (i % 3), 40.0, -74.0, 41.0, -73.0,
                new CompletableFuture<>());
            batchProcessor.addRequest(req);
        }

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        assertEquals(10, batchProcessor.getBatchedRequestCount());
        assertEquals(3, batchProcessor.getAPICallCount());
        double reduction = batchProcessor.getAPIReductionRatio();
        assertTrue(reduction > 0.6);  // Should be 70% reduction
    }

    @Test
    void should_fallback_to_distance_estimate_on_API_failure() throws Exception {
        // Given
        when(routingAPIClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Optional.empty());

        BatchRequest req = new BatchRequest("key", 40.0, -74.0, 41.0, -73.0,
            new CompletableFuture<>());

        // When
        CompletableFuture<BatchResult> future = batchProcessor.addRequest(req);

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        assertTrue(future.isDone());
        BatchResult result = future.get();
        assertTrue(result.isSuccess());
        assertEquals("ESTIMATED", result.routeData().source());
    }

    @Test
    void should_cache_successful_results() throws Exception {
        // Given
        RoutingAPIResponse apiResponse = new RoutingAPIResponse(720, 3200.0, true);
        when(routingAPIClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Optional.of(apiResponse));

        BatchRequest req = new BatchRequest("test-key", 40.0, -74.0, 41.0, -73.0,
            new CompletableFuture<>());

        // When
        CompletableFuture<BatchResult> future = batchProcessor.addRequest(req);

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        assertTrue(future.isDone());
        verify(cacheService).set(eq("test-key"), any(RouteData.class));
    }

    @Test
    void should_handle_API_exceptions_gracefully() throws Exception {
        // Given
        when(routingAPIClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenThrow(new RuntimeException("API error"));

        BatchRequest req = new BatchRequest("key", 40.0, -74.0, 41.0, -73.0,
            new CompletableFuture<>());

        // When
        CompletableFuture<BatchResult> future = batchProcessor.addRequest(req);

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        assertTrue(future.isDone());
        assertThrows(Exception.class, future::get);
    }

    @Test
    void should_notify_all_requests_in_dedup_group() throws Exception {
        // Given
        RoutingAPIResponse apiResponse = new RoutingAPIResponse(720, 3200.0, true);
        when(routingAPIClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Optional.of(apiResponse));

        // Create 5 identical requests
        CompletableFuture<BatchResult>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            BatchRequest req = new BatchRequest("same-key", 40.0, -74.0, 41.0, -73.0,
                new CompletableFuture<>());
            futures[i] = batchProcessor.addRequest(req);
        }

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        for (CompletableFuture<BatchResult> future : futures) {
            assertTrue(future.isDone());
            assertTrue(future.get().isSuccess());
        }
    }

    @Test
    void should_report_correct_metrics() throws Exception {
        // Given
        RoutingAPIResponse apiResponse = new RoutingAPIResponse(720, 3200.0, true);
        when(routingAPIClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
            .thenReturn(Optional.of(apiResponse));

        // Add 20 requests
        for (int i = 0; i < 20; i++) {
            BatchRequest req = new BatchRequest("key" + (i % 4), 40.0, -74.0, 41.0, -73.0,
                new CompletableFuture<>());
            batchProcessor.addRequest(req);
        }

        // Wait for batch processing
        Thread.sleep(200);

        // Then
        assertEquals(20, batchProcessor.getBatchedRequestCount());
        assertEquals(4, batchProcessor.getAPICallCount());
        assertEquals(0.8, batchProcessor.getAPIReductionRatio());  // 80% reduction
    }
}
