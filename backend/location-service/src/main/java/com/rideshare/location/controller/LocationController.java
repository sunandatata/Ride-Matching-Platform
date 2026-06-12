package com.rideshare.location.controller;

import com.rideshare.location.dto.request.LocationUpdateRequest;
import com.rideshare.location.dto.response.LocationResponse;
import com.rideshare.location.dto.response.NearbyDriverResponse;
import com.rideshare.location.service.LocationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for location operations.
 * Thin controller: validation handled by Spring, business logic delegated to service.
 *
 * Endpoints:
 * - PUT /drivers/location - Update driver location (high-throughput)
 * - GET /locations/nearby - Find nearby drivers (for Matching Engine)
 * - GET /drivers/{driverId}/location - Get current location
 * - GET /drivers/{driverId}/location-history - Get location history (1 hour)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Update driver location.
     * High-throughput endpoint, designed for 100k+ updates/sec.
     * Returns 202 Accepted - update is queued for async processing.
     *
     * @param request Location update request (validated by Spring)
     * @return 202 Accepted response
     */
    @PutMapping("/drivers/location")
    public ResponseEntity<Void> updateDriverLocation(
            @Valid @RequestBody LocationUpdateRequest request) {

        log.debug("Received location update for driver {}", request.getDriverId());
        locationService.updateLocation(request);

        // Return 202 Accepted - update is queued
        return ResponseEntity.accepted().build();
    }

    /**
     * Find nearby drivers within a radius.
     * Used by Matching Engine for driver discovery.
     * Must complete in <200ms (p99).
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusKm Search radius in kilometers
     * @return List of nearby drivers with distance
     */
    @GetMapping("/nearby")
    public ResponseEntity<NearbyDriverResponse> findNearbyDrivers(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "5") Integer radiusKm) {

        log.debug("Finding nearby drivers for ({}, {}) within {} km", lat, lng, radiusKm);

        NearbyDriverResponse response = locationService.findNearbyDrivers(lat, lng, radiusKm);

        return ResponseEntity.ok(response);
    }

    /**
     * Get current location of a driver.
     * Fast lookup from Redis Geo index.
     *
     * @param driverId Driver identifier
     * @return Current location or 404 if not found
     */
    @GetMapping("/drivers/{driverId}/location")
    public ResponseEntity<LocationResponse> getDriverLocation(
            @PathVariable String driverId) {

        log.debug("Getting current location for driver {}", driverId);

        return locationService.getDriverLocation(driverId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Get location history for a driver (last 1 hour).
     * Retrieves from PostgreSQL audit trail.
     *
     * @param driverId Driver identifier
     * @return List of locations in descending time order
     */
    @GetMapping("/drivers/{driverId}/location-history")
    public ResponseEntity<?> getLocationHistory(
            @PathVariable String driverId) {

        log.debug("Getting location history for driver {}", driverId);

        var history = locationService.getLocationHistory(driverId);

        return ResponseEntity.ok(new LocationHistoryResponse(driverId, history, history.size()));
    }

    /**
     * Health check / batch stats endpoint.
     * Used for monitoring batching performance.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        var stats = locationService.getBatchStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Response DTO for location history.
     */
    public record LocationHistoryResponse(
            String driverId,
            java.util.List<LocationResponse> locations,
            Integer count) {}
}
