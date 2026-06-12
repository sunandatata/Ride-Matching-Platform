package com.rideshare.location.unit;

import com.rideshare.location.dto.request.LocationUpdateRequest;
import com.rideshare.location.dto.response.LocationResponse;
import com.rideshare.location.dto.response.NearbyDriverResponse;
import com.rideshare.location.exception.LocationServiceException;
import com.rideshare.location.model.LocationUpdate;
import com.rideshare.location.repository.LocationUpdateRepository;
import com.rideshare.location.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.geo.Point;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocationService.
 * Tests business logic for location operations.
 */
@DisplayName("LocationService Tests")
class LocationServiceTest {

    @Mock
    private LocationBatchProcessor batchProcessor;

    @Mock
    private RedisGeoService redisGeoService;

    @Mock
    private LocationUpdateRepository locationRepository;

    @Mock
    private DriverStatusService driverStatusService;

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        locationService = new LocationService(
            batchProcessor,
            redisGeoService,
            locationRepository,
            driverStatusService
        );
    }

    @Test
    @DisplayName("should submit location update to batch processor")
    void testUpdateLocation() {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
            .driverId("driver-123")
            .lat(40.7128)
            .lng(-74.0060)
            .timestamp(Instant.now())
            .source("gps")
            .build();

        assertDoesNotThrow(() -> locationService.updateLocation(request));

        verify(batchProcessor, times(1)).enqueueUpdate(request);
    }

    @Test
    @DisplayName("should retrieve current driver location")
    void testGetDriverLocation() {
        String driverId = "driver-123";
        Point point = new Point(-74.0060, 40.7128);

        when(redisGeoService.getDriverLocation(driverId))
            .thenReturn(Optional.of(point));
        when(driverStatusService.isOnline(driverId))
            .thenReturn(true);

        Optional<LocationResponse> response = locationService.getDriverLocation(driverId);

        assertTrue(response.isPresent());
        assertEquals(driverId, response.get().getDriverId());
        assertEquals(40.7128, response.get().getLatitude());
        assertEquals(-74.0060, response.get().getLongitude());
        assertTrue(response.get().getIsOnline());
    }

    @Test
    @DisplayName("should return empty when driver location not found")
    void testGetDriverLocationNotFound() {
        when(redisGeoService.getDriverLocation("driver-unknown"))
            .thenReturn(Optional.empty());

        Optional<LocationResponse> response = locationService.getDriverLocation("driver-unknown");

        assertTrue(response.isEmpty());
    }

    @Test
    @DisplayName("should find nearby drivers")
    void testFindNearbyDrivers() {
        NearbyDriverResponse mockResponse = NearbyDriverResponse.builder()
            .lat(40.7128)
            .lng(-74.0060)
            .radiusKm(5)
            .drivers(List.of(
                NearbyDriverResponse.DriverLocationInfo.builder()
                    .driverId("driver-1")
                    .latitude(40.7130)
                    .longitude(-74.0062)
                    .distanceMeters(250.0)
                    .build()
            ))
            .count(1)
            .build();

        when(redisGeoService.findNearbyDrivers(40.7128, -74.0060, 5, 100))
            .thenReturn(mockResponse);

        NearbyDriverResponse response = locationService.findNearbyDrivers(40.7128, -74.0060, 5);

        assertNotNull(response);
        assertEquals(1, response.getCount());
        assertEquals("driver-1", response.getDrivers().get(0).getDriverId());
    }

    @Test
    @DisplayName("should reject invalid latitude")
    void testFindNearbyDriversInvalidLatitude() {
        assertThrows(LocationServiceException.class, () ->
            locationService.findNearbyDrivers(91.0, -74.0060, 5)
        );
    }

    @Test
    @DisplayName("should reject invalid longitude")
    void testFindNearbyDriversInvalidLongitude() {
        assertThrows(LocationServiceException.class, () ->
            locationService.findNearbyDrivers(40.7128, 181.0, 5)
        );
    }

    @Test
    @DisplayName("should retrieve location history")
    void testGetLocationHistory() {
        String driverId = "driver-123";
        List<LocationUpdate> mockHistory = List.of(
            LocationUpdate.builder()
                .driverId(driverId)
                .latitude(BigDecimal.valueOf(40.7128))
                .longitude(BigDecimal.valueOf(-74.0060))
                .timestamp(Instant.now())
                .build()
        );

        when(locationRepository.findHistoryByDriverIdAndTimeRange(
            eq(driverId), any(Instant.class), any(Instant.class)))
            .thenReturn(mockHistory);
        when(driverStatusService.isOnline(driverId)).thenReturn(true);

        List<LocationResponse> history = locationService.getLocationHistory(driverId);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(driverId, history.get(0).getDriverId());
    }

    @Test
    @DisplayName("should mark driver as offline")
    void testMarkDriverOffline() {
        String driverId = "driver-123";

        assertDoesNotThrow(() -> locationService.markDriverOffline(driverId));

        verify(redisGeoService, times(1)).removeDriver(driverId);
        verify(driverStatusService, times(1)).markOffline(driverId);
    }

    @Test
    @DisplayName("should get batch statistics")
    void testGetBatchStats() {
        LocationBatchProcessor.BatchStats stats = LocationBatchProcessor.BatchStats.builder()
            .processedCount(100)
            .failedCount(0)
            .currentBatchSize(50)
            .timeSinceLastFlush(50)
            .inputQueueSize(25)
            .build();

        when(batchProcessor.getStats()).thenReturn(stats);

        LocationBatchProcessor.BatchStats result = locationService.getBatchStats();

        assertEquals(100, result.getProcessedCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(50, result.getCurrentBatchSize());
    }
}
