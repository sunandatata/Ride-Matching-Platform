package com.rideshare.driver.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * DTO for batch driver lookup requests.
 * Used by Matching Engine to fetch driver details for multiple drivers at once.
 */
public record BatchDriverLookupRequest(
    @NotEmpty(message = "Driver IDs list cannot be empty")
    @Size(min = 1, max = 1000, message = "Driver IDs list must contain between 1 and 1000 items")
    List<UUID> driverIds
) {}
