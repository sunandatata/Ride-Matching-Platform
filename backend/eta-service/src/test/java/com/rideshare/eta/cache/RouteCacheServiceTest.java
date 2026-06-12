package com.rideshare.eta.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.eta.dto.RouteData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RouteCacheService.
 * Tests caching logic, TTL, grid cell keys, and metrics.
 */
@ExtendWith(MockitoExtension.class)
class RouteCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ObjectMapper objectMapper;
    private RouteCacheService cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cacheService = new RouteCacheService(redisTemplate, objectMapper);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void should_retrieve_cached_route_data() throws Exception {
        // Given
        String cacheKey = "route:4071:-7401:4075:-7395:14:2";
        RouteData expected = new RouteData(12, 3.2, Instant.now(), "LIVE");
        String serialized = objectMapper.writeValueAsString(expected);

        when(valueOps.get(cacheKey)).thenReturn(serialized);

        // When
        Optional<RouteData> result = cacheService.get(cacheKey);

        // Then
        assertTrue(result.isPresent());
        assertEquals(expected.etaMinutes(), result.get().etaMinutes());
        assertEquals(expected.distanceKm(), result.get().distanceKm());
        assertEquals(1, cacheService.getCacheHits());
        assertEquals(0, cacheService.getCacheMisses());
    }

    @Test
    void should_return_empty_on_cache_miss() {
        // Given
        String cacheKey = "route:nonexistent";

        when(valueOps.get(cacheKey)).thenReturn(null);

        // When
        Optional<RouteData> result = cacheService.get(cacheKey);

        // Then
        assertFalse(result.isPresent());
        assertEquals(0, cacheService.getCacheHits());
        assertEquals(1, cacheService.getCacheMisses());
    }

    @Test
    void should_store_route_data_with_TTL() throws Exception {
        // Given
        String cacheKey = "route:4071:-7401:4075:-7395:14:2";
        RouteData data = new RouteData(12, 3.2, Instant.now(), "LIVE");

        // When
        cacheService.set(cacheKey, data);

        // Then
        verify(valueOps).set(
            eq(cacheKey),
            anyString(),
            eq(3600L),  // 1 hour TTL
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void should_calculate_cache_hit_rate() {
        // Given
        when(valueOps.get(anyString())).thenReturn(null);

        // Simulate 8 cache hits and 2 misses
        for (int i = 0; i < 8; i++) {
            when(valueOps.get("hit-" + i)).thenReturn("{}");
            cacheService.get("hit-" + i);
        }

        for (int i = 0; i < 2; i++) {
            when(valueOps.get("miss-" + i)).thenReturn(null);
            cacheService.get("miss-" + i);
        }

        // When
        double hitRate = cacheService.getCacheHitRate();

        // Then
        assertEquals(80.0, hitRate);
    }

    @Test
    void should_return_zero_hit_rate_when_no_requests() {
        // When
        double hitRate = cacheService.getCacheHitRate();

        // Then
        assertEquals(0.0, hitRate);
    }

    @Test
    void should_handle_serialization_error() {
        // Given
        String cacheKey = "route:invalid";

        when(valueOps.get(cacheKey)).thenReturn("invalid-json");

        // When
        Optional<RouteData> result = cacheService.get(cacheKey);

        // Then
        assertFalse(result.isPresent());
        assertEquals(0, cacheService.getCacheHits());
        assertEquals(1, cacheService.getCacheMisses());
    }

    @Test
    void should_delete_cache_entry() {
        // Given
        String cacheKey = "route:test";

        when(redisTemplate.delete(cacheKey)).thenReturn(true);

        // When
        cacheService.delete(cacheKey);

        // Then
        verify(redisTemplate).delete(cacheKey);
    }

    @Test
    void should_reset_metrics() {
        // Given
        when(valueOps.get(anyString())).thenReturn(null);
        cacheService.get("key1");
        cacheService.get("key2");

        assertEquals(0, cacheService.getCacheHits());
        assertEquals(2, cacheService.getCacheMisses());

        // When
        cacheService.resetMetrics();

        // Then
        assertEquals(0, cacheService.getCacheHits());
        assertEquals(0, cacheService.getCacheMisses());
        assertEquals(0.0, cacheService.getCacheHitRate());
    }

    @Test
    void should_handle_grid_cell_cache_keys() throws Exception {
        // Given - cache keys with grid cell coordinates
        String cacheKey1 = "route:4071:-7401:4075:-7395:14:2";
        String cacheKey2 = "route:4072:-7402:4076:-7396:14:2";

        RouteData data1 = new RouteData(12, 3.2, Instant.now(), "LIVE");
        String serialized1 = objectMapper.writeValueAsString(data1);

        RouteData data2 = new RouteData(15, 4.5, Instant.now(), "LIVE");
        String serialized2 = objectMapper.writeValueAsString(data2);

        when(valueOps.get(cacheKey1)).thenReturn(serialized1);
        when(valueOps.get(cacheKey2)).thenReturn(serialized2);

        // When
        Optional<RouteData> result1 = cacheService.get(cacheKey1);
        Optional<RouteData> result2 = cacheService.get(cacheKey2);

        // Then
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals(12, result1.get().etaMinutes());
        assertEquals(15, result2.get().etaMinutes());
    }

    @Test
    void should_track_hit_rate_across_multiple_operations() throws Exception {
        // Given
        when(valueOps.get("hit")).thenReturn("{}");
        when(valueOps.get("miss")).thenReturn(null);

        // When - 70 hits, 30 misses
        for (int i = 0; i < 70; i++) {
            cacheService.get("hit");
        }
        for (int i = 0; i < 30; i++) {
            cacheService.get("miss");
        }

        // Then
        assertEquals(70, cacheService.getCacheHits());
        assertEquals(30, cacheService.getCacheMisses());
        assertEquals(70.0, cacheService.getCacheHitRate());
    }
}
