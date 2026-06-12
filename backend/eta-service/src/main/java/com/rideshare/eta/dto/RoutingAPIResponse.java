package com.rideshare.eta.dto;

/**
 * Generic response from external routing API.
 * Supports multiple routing providers (Google Maps, OSRM, HERE).
 */
public record RoutingAPIResponse(
    Integer durationSeconds,
    Double distanceMeters,
    Boolean isSuccessful
) {
    /**
     * Converts API response to RouteData format.
     */
    public RouteData toRouteData(String source) {
        return new RouteData(
            (durationSeconds / 60),  // Convert seconds to minutes
            (distanceMeters / 1000),  // Convert meters to km
            java.time.Instant.now(),
            source
        );
    }
}
