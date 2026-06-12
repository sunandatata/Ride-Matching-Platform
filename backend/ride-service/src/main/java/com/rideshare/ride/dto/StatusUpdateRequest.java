package com.rideshare.ride.dto;

import com.rideshare.ride.entity.RideStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating ride status via API.
 * Handles state transitions triggered externally.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusUpdateRequest {

    @NotNull(message = "Status is required")
    private RideStatus status;

    private String initiatorId;

    private String initiatorType; // DRIVER, RIDER, SYSTEM
}
