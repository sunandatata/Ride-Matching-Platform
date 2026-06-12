package com.rideshare.ride.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new ride request.
 * Validates all required input fields from the rider.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRideRequest {

    @NotBlank(message = "Rider ID is required")
    private String riderId;

    @NotNull(message = "Pickup latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double pickupLongitude;

    @NotBlank(message = "Pickup address is required")
    @Size(min = 5, max = 255, message = "Pickup address must be between 5 and 255 characters")
    private String pickupAddress;

    @NotNull(message = "Dropoff latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double dropoffLatitude;

    @NotNull(message = "Dropoff longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double dropoffLongitude;

    @NotBlank(message = "Dropoff address is required")
    @Size(min = 5, max = 255, message = "Dropoff address must be between 5 and 255 characters")
    private String dropoffAddress;

    @NotNull(message = "Passenger count is required")
    @Min(value = 1, message = "Passenger count must be at least 1")
    @Max(value = 6, message = "Passenger count cannot exceed 6")
    private Integer passengerCount;
}
