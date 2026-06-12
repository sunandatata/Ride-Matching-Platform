package com.rideshare.location.unit;

import com.rideshare.location.dto.response.NearbyDriverResponse;
import com.rideshare.location.exception.LocationServiceException;
import com.rideshare.location.model.LocationUpdate;
import com.rideshare.location.repository.DriverStatusRepository;
import com.rideshare.location.service.RedisGeoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.GeoOperations;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RedisGeoService.
 * Tests high-performance Redis Geo operations.
 */
@DisplayName("RedisGeoService Tests")
class RedisGeoServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOps;

    @Mock
    private DriverStatusRepository driverStatusRepository;

    private RedisGeoService redisGeoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        redisGeoService = new RedisGeoService(redisTemplate, driverStatusRepository);
    }

    @Test
    @DisplayName("should add single location to Redis Geo")
    void testAddLocation() {
        LocationUpdate update = LocationUpdate.builder()
            .driverId("driver-123")
            .latitude(BigDecimal.valueOf(40.7128))
            .longitude(BigDecimal.valueOf(-74.0060))
            .timestamp(Instant.now())
            .build();

        when(redisTemplate.opsForGeo()).thenReturn(geoOps);
        when(geoOps.add(anyString(), any(Point.class), anyString())).thenReturn(1L);

        assertDoesNotThrow(() -> redisGeoService.addLocation(update));

        verify(geoOps, times(1)).add(anyString(), any(Point.class), eq("driver-123"));
    }

    @Test
    @DisplayName("should handle Redis error when adding location")
    void testAddLocationRedisError() {
        LocationUpdate update = LocationUpdate.builder()
            .driverId("driver-123")
            .latitude(BigDecimal.valueOf(40.7128))
            .longitude(BigDecimal.valueOf(-74.0060))
            .timestamp(Instant.now())
            .build();

        when(redisTemplate.opsForGeo()).thenThrow(new RuntimeException("Redis connection failed"));

        assertThrows(LocationServiceException.class, () -> redisGeoService.addLocation(update));
    }

    @Test
    @DisplayName("should batch add multiple locations using pipeline")
    void testAddLocationsBatch() {
        List<LocationUpdate> updates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            updates.add(LocationUpdate.builder()
                .driverId("driver-" + i)
                .latitude(BigDecimal.valueOf(40.7128 + i * 0.001))
                .longitude(BigDecimal.valueOf(-74.0060 + i * 0.001))
                .timestamp(Instant.now())
                .build());
        }

        when(redisTemplate.executePipelined(any())).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> redisGeoService.addLocationsBatch(updates));

        verify(redisTemplate, times(1)).executePipelined(any());
    }

    @Test
    @DisplayName("should skip empty batch")
    void testAddLocationsBatchEmpty() {
        List<LocationUpdate> updates = new ArrayList<>();

        when(redisTemplate.executePipelined(any())).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> redisGeoService.addLocationsBatch(updates));

        verify(redisTemplate, never()).executePipelined(any());
    }

    @Test
    @DisplayName("should find nearby drivers")
    void testFindNearbyDrivers() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOps);
        when(geoOps.radius(anyString(), any())).thenReturn(null);

        NearbyDriverResponse response = redisGeoService.findNearbyDrivers(
            40.7128, -74.0060, 5, 100);

        assertNotNull(response);
        assertEquals(40.7128, response.getLat());
        assertEquals(-74.0060, response.getLng());
        assertEquals(5, response.getRadiusKm());
    }

    @Test
    @DisplayName("should get driver location from Redis")
    void testGetDriverLocation() {
        Point expectedPoint = new Point(-74.0060, 40.7128);

        when(redisTemplate.opsForGeo()).thenReturn(geoOps);
        when(geoOps.position(anyString(), anyString()))
            .thenReturn(List.of(expectedPoint));

        Optional<Point> location = redisGeoService.getDriverLocation("driver-123");

        assertTrue(location.isPresent());
        assertEquals(expectedPoint, location.get());
    }

    @Test
    @DisplayName("should return empty when driver location not found")
    void testGetDriverLocationNotFound() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOps);
        when(geoOps.position(anyString(), anyString()))
            .thenReturn(new ArrayList<>());

        Optional<Point> location = redisGeoService.getDriverLocation("driver-unknown");

        assertTrue(location.isEmpty());
    }

    @Test
    @DisplayName("should remove driver from Redis Geo")
    void testRemoveDriver() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOps);
        when(geoOps.remove(anyString(), anyString())).thenReturn(1L);

        assertDoesNotThrow(() -> redisGeoService.removeDriver("driver-123"));

        verify(geoOps, times(1)).remove(anyString(), eq("driver-123"));
    }

    @Test
    @DisplayName("should mark driver as online")
    void testMarkDriverOnline() {
        when(redisTemplate.opsForValue()).thenReturn(any());

        assertDoesNotThrow(() -> redisGeoService.markDriverOnline("driver-123"));
    }
}
