package com.rideshare.eta.service;

import com.rideshare.eta.batch.BatchRequest;
import com.rideshare.eta.batch.BatchResult;
import com.rideshare.eta.batch.RoutingAPIBatchProcessor;
import com.rideshare.eta.cache.RouteCacheService;
import com.rideshare.eta.dto.ETARequest;
import com.rideshare.eta.dto.ETAResponse;
import com.rideshare.eta.dto.RouteData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ETAService.
 * Tests caching logic, batching integration, and fallback behavior.
 */
@ExtendWith(MockitoExtension.class)
class ETAServiceTest {

    @Mock
    private RouteCacheService cacheService;

    @Mock
    private RoutingAPIBatchProcessor batchProcessor;

    private ETAService etaService;

    @BeforeEach
    void setUp() {
        etaService = new ETAService(cacheService, batchProcessor);
    }

    @Test
    void should_return_cached_ETA_when_cache_hit() {
        // Given
        ETARequest request = new ETARequest(40.7128, -74.0060, 40.7580, -73.9855);
        RouteData cachedData = new RouteData(12, 3.2, Instant.now(), "LIVE");

        when(cacheService.get(anyString())).thenReturn(Optional.of(cachedData));

        // When
        ETAResponse response = etaService.calculateETA(request);

        // Then
        assertEquals(12, response.etaMinutes());
        assertEquals(3.2, response.distanceKm());
        assertEquals(ETAResponse.ETAStatus.CACHED, response.status());

        verify(cacheService).get(anyString());
        verify(batchProcessor, never()).addRequest(any());
    }

    @Test
    void should_use_batch_processor_on_cache_miss() {
        // Given
        ETARequest request = new ETARequest(40.7128, -74.0060, 40.7580, -73.9855);
        RouteData apiData = new RouteData(12, 3.2, Instant.now(), "LIVE");
        BatchResult batchResult = BatchResult.success("test-key", apiData);

        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        CompletableFuture<BatchResult> future = CompletableFuture.completedFuture(batchResult);
        when(batchProcessor.addRequest(any(BatchRequest.class))).thenReturn(future);

        // When
        ETAResponse response = etaService.calculateETA(request);

        // Then
        assertEquals(12, response.etaMinutes());
        assertEquals(3.2, response.distanceKm());
        assertEquals(ETAResponse.ETAStatus.LIVE, response.status());

        verify(cacheService).get(anyString());
        verify(batchProcessor).addRequest(any(BatchRequest.class));
    }

    @Test
    void should_fallback_to_distance_estimate_on_batch_timeout() {
        // Given
        ETARequest request = new ETARequest(40.7128, -74.0060, 40.7580, -73.9855);

        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        CompletableFuture<BatchResult> future = new CompletableFuture<>();
        // Never complete, simulating timeout
        when(batchProcessor.addRequest(any(BatchRequest.class))).thenReturn(future);

        // When
        ETAResponse response = etaService.calculateETA(request);

        // Then
        assertNotNull(response);
        assertEquals(ETAResponse.ETAStatus.ESTIMATED, response.status());
        assertTrue(response.etaMinutes() > 0);
        assertTrue(response.distanceKm() > 0);
    }

    @Test
    void should_handle_validation_error_gracefully() {
        // Given - invalid coordinates
        ETARequest request = new ETARequest(91.0, 0.0, 0.0, 0.0);  // Invalid latitude

        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        // When - this would normally be caught by validation annotations
        // For testing error handling in service layer
        assertDoesNotThrow(() -> {
            etaService.calculateETA(request);
        });
    }

    @Test
    void should_return_ESTIMATED_status_from_distance_calculation() {
        // Given
        ETARequest request = new ETARequest(0.0, 0.0, 0.01, 0.01);  // ~1km away

        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        CompletableFuture<BatchResult> future = CompletableFuture.failedFuture(
            new RuntimeException("API failed")
        );
        when(batchProcessor.addRequest(any(BatchRequest.class))).thenReturn(future);

        // When
        ETAResponse response = etaService.calculateETA(request);

        // Then
        assertEquals(ETAResponse.ETAStatus.ESTIMATED, response.status());
        assertTrue(response.distanceKm() < 2);  // Should be close to 1km
    }

    @Test
    void should_complete_within_timeout_ms() {
        // Given
        ETARequest request = new ETARequest(40.7128, -74.0060, 40.7580, -73.9855);

        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        // Simulate slow batch processor
        CompletableFuture<BatchResult> future = new CompletableFuture<>();
        when(batchProcessor.addRequest(any(BatchRequest.class))).thenReturn(future);

        // When
        long startTime = System.currentTimeMillis();
        ETAResponse response = etaService.calculateETA(request);
        long elapsed = System.currentTimeMillis() - startTime;

        // Then
        assertNotNull(response);
        assertTrue(elapsed < 100, "ETA calculation should complete within 100ms");
    }

    @Test
    void should_handle_null_response_from_batch_processor() {
        // Given
        ETARequest request = new ETARequest(40.7128, -74.0060, 40.7580, -73.9855);

        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        CompletableFuture<BatchResult> future = CompletableFuture.completedFuture(null);
        when(batchProcessor.addRequest(any(BatchRequest.class))).thenReturn(future);

        // When
        ETAResponse response = etaService.calculateETA(request);

        // Then
        assertNotNull(response);
        assertEquals(ETAResponse.ETAStatus.ESTIMATED, response.status());
    }

    @Test
    void should_use_estimated_status_when_cache_service_fails() {
        // Given
        ETARequest request = new ETARequest(40.7128, -74.0060, 40.7580, -73.9855);

        when(cacheService.get(anyString())).thenThrow(new RuntimeException("Cache error"));

        // When
        ETAResponse response = etaService.calculateETA(request);

        // Then
        assertNotNull(response);
        assertEquals(ETAResponse.ETAStatus.ESTIMATED, response.status());
    }
}
