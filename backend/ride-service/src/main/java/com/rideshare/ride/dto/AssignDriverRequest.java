package com.rideshare.ride.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * DTO for driver assignment from the Matching Engine.
 * Called by Matching Service when a driver is selected for a ride.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignDriverRequest {

    @NotBlank(message = "Driver ID is required")
    private String driverId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Estimated fare must be greater than 0")
    private BigDecimal estimatedFare;

    private Integer estimatedDurationSeconds;

    private Integer driverETA;
}
