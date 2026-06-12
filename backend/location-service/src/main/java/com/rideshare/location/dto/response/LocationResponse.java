package com.rideshare.location.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Response DTO for driver location information.
 * Returned by location queries and current location endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationResponse {

    private String driverId;

    private Double latitude;

    private Double longitude;

    private Integer heading;

    private Double speed;

    private Double accuracy;

    private Instant timestamp;

    private String source;

    private Boolean isOnline;
}
