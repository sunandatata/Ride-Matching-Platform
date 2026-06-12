package com.rideshare.location.service;

import com.rideshare.location.dto.request.LocationUpdateRequest;
import com.rideshare.location.dto.response.LocationResponse;
import com.rideshare.location.dto.response.NearbyDriverResponse;
import com.rideshare.location.exception.LocationServiceException;
import com.rideshare.location.model.LocationUpdate;
import com.rideshare.location.repository.LocationUpdateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Main Location Service orchestrating location updates, queries, and history.
 * Provides high-level business operations for the Location microservice.
 */
@Slf4j
@Service
public class LocationService {

    private final LocationBatchProcessor batchProcessor;
    private final RedisGeoService redisGeoService;
    private final LocationUpdateRepository locationRepository;
    private final DriverStatusService driverStatusService;

    public LocationService(
            LocationBatchProcessor batchProcessor,
            RedisGeoService redisGeoService,
            LocationUpdateRepository locationRepository,
            DriverStatusService driverStatusService) {

        this.batchProcessor = batchProcessor;
        this.redisGeoService = redisGeoService;
        this.locationRepository = locationRepository;
        this.driverStatusService = driverStatusService;
    }

    /**
     * Submit a driver location update for batching and processing.
     * Enqueues update in batch processor for high-throughput handling.
     *
     * @param request Location update request (already validated by controller)
     */
    public void updateLocation(LocationUpdateRequest request) {
        log.debug("Received location update for driver {}", request.getDriverId());
        batchProcessor.enqueueUpdate(request);
    }

    /**
     * Get current location of a driver from Redis Geo (hot path).
     * Returns null if driver not found or offline.
     *
     * @param driverId Driver identifier
     * @return Current location or empty if not found
     */
    public Optional<LocationResponse> getDriverLocation(String driverId) {
        try {
            Optional<Point> location = redisGeoService.getDriverLocation(driverId);

            if (location.isEmpty()) {
                log.debug("Location not found for driver {} in Redis", driverId);
                return Optional.empty();
            }

            Point point = location.get();
            boolean isOnline = driverStatusService.isOnline(driverId);

            LocationResponse response = LocationResponse.builder()
                .driverId(driverId)
                .latitude(point.getY())
                .longitude(point.getX())
                .isOnline(isOnline)
                .build();

            return Optional.of(response);

        } catch (Exception e) {
            log.error("Failed to get current location for driver {}", driverId, e);
            throw new LocationServiceException(
                "Failed to retrieve driver location",
                "LOCATION_RETRIEVAL_ERROR",
                e
            );
        }
    }

    /**
     * Find available drivers near a location.
     * Used by Matching Engine for driver discovery.
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusKm Search radius in kilometers
     * @return Nearby drivers with distance information
     */
    public NearbyDriverResponse findNearbyDrivers(double latitude, double longitude, int radiusKm) {
        validateCoordinates(latitude, longitude);

        try {
            log.debug("Finding nearby drivers for ({}, {}) within {} km",
                     latitude, longitude, radiusKm);

            return redisGeoService.findNearbyDrivers(latitude, longitude, radiusKm, 100);

        } catch (Exception e) {
            log.error("Failed to find nearby drivers", e);
            throw new LocationServiceException(
                "Failed to find nearby drivers",
                "NEARBY_DRIVER_ERROR",
                e
            );
        }
    }

    /**
     * Get location history for a driver (last 1 hour).
     * Retrieved from PostgreSQL audit trail.
     *
     * @param driverId Driver identifier
     * @return List of locations in descending time order
     */
    @Transactional(readOnly = true)
    public List<LocationResponse> getLocationHistory(String driverId) {
        try {
            Instant oneHourAgo = Instant.now().minusSeconds(3600);
            Instant now = Instant.now();

            List<LocationUpdate> updates = locationRepository
                .findHistoryByDriverIdAndTimeRange(driverId, oneHourAgo, now);

            log.debug("Retrieved {} location history points for driver {}",
                     updates.size(), driverId);

            return updates.stream()
                .map(this::convertToResponse)
                .toList();

        } catch (Exception e) {
            log.error("Failed to retrieve location history for driver {}", driverId, e);
            throw new LocationServiceException(
                "Failed to retrieve location history",
                "HISTORY_RETRIEVAL_ERROR",
                e
            );
        }
    }

    /**
     * Mark driver as offline (called when driver disconnects).
     *
     * @param driverId Driver identifier
     */
    public void markDriverOffline(String driverId) {
        try {
            log.info("Marking driver {} as offline", driverId);
            redisGeoService.removeDriver(driverId);
            driverStatusService.markOffline(driverId);

        } catch (Exception e) {
            log.error("Failed to mark driver {} as offline", driverId, e);
        }
    }

    /**
     * Get batching statistics for monitoring.
     */
    public LocationBatchProcessor.BatchStats getBatchStats() {
        return batchProcessor.getStats();
    }

    /**
     * Convert LocationUpdate entity to response DTO.
     */
    private LocationResponse convertToResponse(LocationUpdate update) {
        return LocationResponse.builder()
            .driverId(update.getDriverId())
            .latitude(update.getLatitude().doubleValue())
            .longitude(update.getLongitude().doubleValue())
            .heading(update.getHeading())
            .speed(update.getSpeed())
            .accuracy(update.getAccuracy())
            .timestamp(update.getTimestamp())
            .source(update.getSource())
            .isOnline(driverStatusService.isOnline(update.getDriverId()))
            .build();
    }

    /**
     * Validate coordinates are within valid range.
     */
    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new LocationServiceException(
                "Invalid latitude: " + latitude,
                "INVALID_COORDINATES"
            );
        }
        if (longitude < -180 || longitude > 180) {
            throw new LocationServiceException(
                "Invalid longitude: " + longitude,
                "INVALID_COORDINATES"
            );
        }
    }
}
