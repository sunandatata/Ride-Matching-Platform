package com.rideshare.driver.dto;

import jakarta.validation.constraints.*;

/**
 * DTO for updating driver vehicle information.
 */
public record VehicleUpdateRequest(
    @NotBlank(message = "Vehicle make is required")
    @Size(min = 2, max = 100, message = "Vehicle make must be between 2 and 100 characters")
    String vehicleMake,

    @NotBlank(message = "Vehicle model is required")
    @Size(min = 2, max = 100, message = "Vehicle model must be between 2 and 100 characters")
    String vehicleModel,

    @NotNull(message = "Vehicle year is required")
    @Min(value = 1990, message = "Vehicle year must be 1990 or later")
    @Max(value = 2100, message = "Vehicle year must be valid")
    Integer vehicleYear,

    @NotBlank(message = "Vehicle color is required")
    @Size(min = 2, max = 50, message = "Vehicle color must be between 2 and 50 characters")
    String vehicleColor,

    @NotBlank(message = "Vehicle license plate is required")
    @Size(min = 3, max = 50, message = "Vehicle license plate must be between 3 and 50 characters")
    String vehicleLicensePlate,

    @NotNull(message = "Vehicle capacity is required")
    @Min(value = 1, message = "Vehicle capacity must be at least 1")
    @Max(value = 8, message = "Vehicle capacity cannot exceed 8")
    Integer vehicleCapacity,

    @NotNull(message = "Vehicle type is required")
    String vehicleType
) {}
