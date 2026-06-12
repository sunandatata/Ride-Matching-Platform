package com.rideshare.ride.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ride entity representing the core ride data model.
 * Uses JPA for persistence with PostgreSQL backing.
 */
@Entity
@Table(name = "rides", indexes = {
    @Index(name = "idx_rider_id", columnList = "rider_id"),
    @Index(name = "idx_driver_id", columnList = "driver_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ride {

    @Id
    private String id;

    @Column(nullable = false)
    private String riderId;

    @Column
    private String driverId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RideStatus status;

    @Column(nullable = false)
    private Double pickupLatitude;

    @Column(nullable = false)
    private Double pickupLongitude;

    @Column(nullable = false)
    private String pickupAddress;

    @Column(nullable = false)
    private Double dropoffLatitude;

    @Column(nullable = false)
    private Double dropoffLongitude;

    @Column(nullable = false)
    private String dropoffAddress;

    @Column(nullable = false)
    private Integer passengerCount;

    @Column
    private BigDecimal estimatedFare;

    @Column
    private BigDecimal actualFare;

    @Column
    private Integer estimatedDurationSeconds;

    @Column
    private Integer actualDurationSeconds;

    @Column(name = "driver_eta")
    private Integer driverETA;

    @Column
    private LocalDateTime matchedAt;

    @Column
    private LocalDateTime acceptedAt;

    @Column
    private LocalDateTime arrivedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private LocalDateTime cancelledAt;

    @Column
    private String cancellationReason;

    @Column
    private String cancellationInitiator;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private Integer driverRating;

    @Column
    private Integer riderRating;

    @Column
    private String driverFeedback;

    @Column
    private String riderFeedback;

    @Column(nullable = false)
    private Integer shardId;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
