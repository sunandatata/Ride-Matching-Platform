package com.rideshare.ride.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rideshare.ride.entity.RideStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for ride response in API endpoints.
 * Includes all relevant ride information for display to clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RideResponse {

    private String id;
    private String riderId;
    private String driverId;
    private RideStatus status;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupAddress;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String dropoffAddress;
    private Integer passengerCount;
    private BigDecimal estimatedFare;
    private BigDecimal actualFare;
    private Integer estimatedDurationSeconds;
    private Integer actualDurationSeconds;
    private Integer driverETA;
    private LocalDateTime matchedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private String cancellationInitiator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer driverRating;
    private Integer riderRating;
    private String driverFeedback;
    private String riderFeedback;
}
