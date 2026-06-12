package com.rideshare.driver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * DTO for updating driver profile information.
 */
public record DriverUpdateRequest(
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    String firstName,

    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    String lastName,

    @Email(message = "Email must be valid")
    String email,

    String profilePhotoUrl
) {}
