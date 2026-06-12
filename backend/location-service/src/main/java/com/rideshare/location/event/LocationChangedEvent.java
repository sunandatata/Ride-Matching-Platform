package com.rideshare.location.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Domain event published when driver location changes.
 * Consumed by Matching Engine, Notification Service, and other components.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationChangedEvent {

    private String driverId;

    private Double latitude;

    private Double longitude;

    private Integer heading;

    private Double speed;

    private Instant timestamp;

    private String source;

    private Instant eventPublishedAt;
}
