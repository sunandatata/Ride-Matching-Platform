package com.rideshare.ride.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * RideEvent entity for event sourcing pattern.
 * Provides audit trail of all state changes and important events.
 * Used for debugging, compliance, and analytics.
 */
@Entity
@Table(name = "ride_events", indexes = {
    @Index(name = "idx_ride_id_event", columnList = "ride_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at_event", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String rideId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RideEventType eventType;

    @Column
    @Enumerated(EnumType.STRING)
    private RideStatus previousStatus;

    @Column
    @Enumerated(EnumType.STRING)
    private RideStatus newStatus;

    @Column
    private String initiatorId;

    @Column
    private String initiatorType; // RIDER, DRIVER, SYSTEM

    @Column(columnDefinition = "TEXT")
    private String eventData; // JSON payload for event details

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
