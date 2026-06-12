package com.rideshare.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event: Rider has requested a new ride
 * Source: ride-service
 * Topic: ride-requested
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideRequestedEvent {
    private String rideId;
    private String riderId;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropoffLat;
    private Double dropoffLng;
    private String pickupAddress;
    private String dropoffAddress;
    private Instant requestedAt;
}
