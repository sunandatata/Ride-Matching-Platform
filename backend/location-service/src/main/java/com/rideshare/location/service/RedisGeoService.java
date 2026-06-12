package com.rideshare.location.service;

import com.rideshare.location.dto.response.NearbyDriverResponse;
import com.rideshare.location.exception.LocationServiceException;
import com.rideshare.location.model.LocationUpdate;
import com.rideshare.location.repository.DriverStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * High-performance Redis Geo operations for driver location indexing.
 * All operations use pipelining for batch efficiency.
 * Critical for 100k+ location update throughput.
 */
@Slf4j
@Service
public class RedisGeoService {

    private static final String DRIVER_LOCATIONS_KEY = "driver_locations";
    private static final String DRIVER_STATUS_KEY_PREFIX = "driver:status:";

    private final RedisTemplate<String, String> redisTemplate;
    private final DriverStatusRepository driverStatusRepository;

    public RedisGeoService(RedisTemplate<String, String> redisTemplate,
                          DriverStatusRepository driverStatusRepository) {
        this.redisTemplate = redisTemplate;
        this.driverStatusRepository = driverStatusRepository;
    }

    /**
     * Add a single driver location to Redis Geo index.
     * Idempotent - replaces existing location if driver already exists.
     *
     * @param update Location update to add
     */
    public void addLocation(LocationUpdate update) {
        try {
            Point point = new Point(update.getLongitude().doubleValue(),
                                   update.getLatitude().doubleValue());
            redisTemplate.opsForGeo().add(DRIVER_LOCATIONS_KEY, point, update.getDriverId());
            log.debug("Added location for driver {} at ({}, {})",
                     update.getDriverId(), update.getLatitude(), update.getLongitude());
        } catch (Exception e) {
            log.error("Failed to add location for driver {}", update.getDriverId(), e);
            throw new LocationServiceException(
                "Failed to index location in Redis",
                "REDIS_ERROR",
                e
            );
        }
    }

    /**
     * Batch add multiple driver locations using pipelined operations.
     * Critical for high-throughput: batches of 500+ locations.
     *
     * @param updates List of location updates to add
     */
    public void addLocationsBatch(List<LocationUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            redisTemplate.executePipelined((connection) -> {
                for (LocationUpdate update : updates) {
                    Point point = new Point(
                        update.getLongitude().doubleValue(),
                        update.getLatitude().doubleValue()
                    );
                    connection.geoAdd(
                        DRIVER_LOCATIONS_KEY.getBytes(),
                        point.getX(),
                        point.getY(),
                        update.getDriverId().getBytes()
                    );
                }
                return null;
            });

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Batch added {} locations to Redis Geo in {}ms", updates.size(), elapsed);

        } catch (Exception e) {
            log.error("Failed to batch add {} locations", updates.size(), e);
            throw new LocationServiceException(
                "Failed to batch index locations in Redis",
                "REDIS_ERROR",
                e
            );
        }
    }

    /**
     * Find drivers within a radius from a location.
     * Used by Matching Engine to discover available drivers.
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusKm Search radius in kilometers
     * @param maxResults Maximum number of results to return
     * @return Nearby drivers with distance information
     */
    public NearbyDriverResponse findNearbyDrivers(
            double latitude,
            double longitude,
            int radiusKm,
            int maxResults) {

        try {
            Point center = new Point(longitude, latitude);
            Distance radius = new Distance(radiusKm, Metrics.KILOMETERS);
            Circle circle = new Circle(center, radius);

            long startTime = System.currentTimeMillis();

            Set<RedisGeoCommands.GeoLocation<String>> geoLocations =
                redisTemplate.opsForGeo().radius(DRIVER_LOCATIONS_KEY, circle,
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .withCoord()
                        .withDist()
                        .count(maxResults)
                        .sortDescending());

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Found {} nearby drivers in {}ms",
                     geoLocations != null ? geoLocations.size() : 0, elapsed);

            List<NearbyDriverResponse.DriverLocationInfo> drivers = new ArrayList<>();
            if (geoLocations != null) {
                drivers = geoLocations.stream()
                    .map(geoLocation -> buildDriverLocationInfo(geoLocation, latitude, longitude))
                    .collect(Collectors.toList());
            }

            return NearbyDriverResponse.builder()
                .lat(latitude)
                .lng(longitude)
                .radiusKm(radiusKm)
                .drivers(drivers)
                .count(drivers.size())
                .build();

        } catch (Exception e) {
            log.error("Failed to find nearby drivers for ({}, {})", latitude, longitude, e);
            throw new LocationServiceException(
                "Failed to query nearby drivers",
                "REDIS_ERROR",
                e
            );
        }
    }

    /**
     * Get current location of a specific driver from Redis Geo.
     *
     * @param driverId Driver identifier
     * @return Current location or empty if driver not found
     */
    public Optional<Point> getDriverLocation(String driverId) {
        try {
            Point location = redisTemplate.opsForGeo().position(DRIVER_LOCATIONS_KEY, driverId)
                .stream()
                .findFirst()
                .orElse(null);

            return Optional.ofNullable(location);

        } catch (Exception e) {
            log.error("Failed to get location for driver {}", driverId, e);
            throw new LocationServiceException(
                "Failed to retrieve driver location",
                "REDIS_ERROR",
                e
            );
        }
    }

    /**
     * Remove driver from Redis Geo index.
     * Called when driver goes offline.
     *
     * @param driverId Driver identifier
     */
    public void removeDriver(String driverId) {
        try {
            redisTemplate.opsForGeo().remove(DRIVER_LOCATIONS_KEY, driverId);
            log.debug("Removed driver {} from Redis Geo", driverId);
        } catch (Exception e) {
            log.error("Failed to remove driver {} from Redis Geo", driverId, e);
            // Non-critical error, don't throw
        }
    }

    /**
     * Mark driver as online in Redis cache (short-lived, <5min TTL).
     * Used for quick status checks without DB query.
     *
     * @param driverId Driver identifier
     */
    public void markDriverOnline(String driverId) {
        try {
            String key = DRIVER_STATUS_KEY_PREFIX + driverId;
            redisTemplate.opsForValue().set(key, "online");
            redisTemplate.expire(key, java.time.Duration.ofMinutes(5));
        } catch (Exception e) {
            log.error("Failed to mark driver {} as online", driverId, e);
        }
    }

    /**
     * Check if driver is cached as online.
     * Returns false if key not found or expired (treated as offline).
     *
     * @param driverId Driver identifier
     * @return True if driver is marked online
     */
    public boolean isDriverOnlineCached(String driverId) {
        try {
            String key = DRIVER_STATUS_KEY_PREFIX + driverId;
            Object value = redisTemplate.opsForValue().get(key);
            return "online".equals(value);
        } catch (Exception e) {
            log.error("Failed to check online status for driver {}", driverId, e);
            return false;
        }
    }

    /**
     * Build DriverLocationInfo from Redis GeoLocation.
     */
    private NearbyDriverResponse.DriverLocationInfo buildDriverLocationInfo(
            RedisGeoCommands.GeoLocation<String> geoLocation,
            double queryLat,
            double queryLng) {

        Point point = geoLocation.getPoint();
        Double distance = geoLocation.getDistance() != null
            ? geoLocation.getDistance().getValue() * 1000  // Convert km to meters
            : 0.0;

        return NearbyDriverResponse.DriverLocationInfo.builder()
            .driverId(geoLocation.getMember())
            .latitude(point.getY())
            .longitude(point.getX())
            .distanceMeters(distance)
            .isOnline(isDriverOnlineCached(geoLocation.getMember()))
            .build();
    }
}
