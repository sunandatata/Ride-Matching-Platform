package com.rideshare.driver.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating driver availability status.
 * Supports status transitions: ONLINE, OFFLINE, ON_RIDE, BREAK.
 */
public record AvailabilityStatusUpdateRequest(
    @NotNull(message = "Availability status is required")
    String status
) {
    /**
     * Validate that status is a valid availability status.
     *
     * @return true if valid
     */
    public boolean isValidStatus() {
        return "ONLINE".equals(status) ||
               "OFFLINE".equals(status) ||
               "ON_RIDE".equals(status) ||
               "BREAK".equals(status);
    }
}
