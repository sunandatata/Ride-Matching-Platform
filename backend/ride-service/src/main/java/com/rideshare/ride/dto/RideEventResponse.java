package com.rideshare.ride.dto;

import com.rideshare.ride.entity.RideEventType;
import com.rideshare.ride.entity.RideStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO for ride event response.
 * Used for debugging and event history endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RideEventResponse {

    private Long id;
    private String rideId;
    private RideEventType eventType;
    private RideStatus previousStatus;
    private RideStatus newStatus;
    private String initiatorId;
    private String initiatorType;
    private String eventData;
    private LocalDateTime createdAt;
}
