package com.rideshare.driver.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * DTO for driver registration requests.
 * Validates all required fields for new driver creation.
 */
public record DriverRegistrationRequest(
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    String phoneNumber,

    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    String firstName,

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    String lastName,

    @NotNull(message = "Date of birth is required")
    @PastOrPresent(message = "Date of birth cannot be in the future")
    LocalDate dateOfBirth,

    @NotBlank(message = "License number is required")
    @Size(min = 5, max = 50, message = "License number must be between 5 and 50 characters")
    String licenseNumber,

    @NotBlank(message = "License state is required")
    @Size(min = 2, max = 50, message = "License state must be between 2 and 50 characters")
    String licenseState,

    @NotNull(message = "License expiry date is required")
    @FutureOrPresent(message = "License must not be expired")
    LocalDate licenseExpiryDate,

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
    Integer vehicleCapacity
) {}
