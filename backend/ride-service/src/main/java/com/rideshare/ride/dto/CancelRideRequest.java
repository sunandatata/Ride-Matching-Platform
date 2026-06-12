package com.rideshare.ride.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for cancelling a ride.
 * Can be initiated by rider, driver, or system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelRideRequest {

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotBlank(message = "Initiator ID is required")
    private String initiatorId;

    @NotBlank(message = "Initiator type is required (RIDER, DRIVER, or SYSTEM)")
    private String initiatorType;
}
