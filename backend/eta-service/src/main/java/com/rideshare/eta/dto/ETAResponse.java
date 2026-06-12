package com.rideshare.eta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response containing ETA and distance information.
 * ETAStatus indicates data source: CACHED, LIVE, or ESTIMATED.
 */
public record ETAResponse(
    @JsonProperty("eta_minutes")
    Integer etaMinutes,

    @JsonProperty("distance_km")
    Double distanceKm,

    @JsonProperty("status")
    ETAStatus status
) {
    public enum ETAStatus {
        CACHED,       // Result from cache hit
        LIVE,         // Result from external routing API
        ESTIMATED     // Fallback distance-based estimation
    }
}
