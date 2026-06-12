package com.rideshare.location.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Response DTO for nearby drivers query.
 * Used by Matching Engine to discover available drivers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NearbyDriverResponse {

    private Double lat;

    private Double lng;

    private Integer radiusKm;

    private List<DriverLocationInfo> drivers;

    private Integer count;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DriverLocationInfo {

        private String driverId;

        private Double latitude;

        private Double longitude;

        private Double distanceMeters;  // Distance from query center

        private Integer heading;

        private Double speed;

        private Boolean isOnline;
    }
}
