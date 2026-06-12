package com.rideshare.location.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Request DTO for driver location updates.
 * Validated on ingestion before batching.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationUpdateRequest {

    @NotBlank(message = "Driver ID is required")
    private String driverId;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90", message = "Latitude must be >= -90")
    @DecimalMax(value = "90", message = "Latitude must be <= 90")
    private Double lat;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180", message = "Longitude must be >= -180")
    @DecimalMax(value = "180", message = "Longitude must be <= 180")
    private Double lng;

    @Min(value = 0, message = "Heading must be >= 0")
    @Max(value = 360, message = "Heading must be <= 360")
    private Integer heading;

    @Min(value = 0, message = "Speed cannot be negative")
    private Double speed;  // m/s

    @Min(value = 0, message = "Accuracy must be >= 0")
    private Double accuracy;  // meters

    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    @Pattern(regexp = "^(gps|network|fused)$", message = "Source must be gps, network, or fused")
    private String source;
}
