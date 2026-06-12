package com.rideshare.location.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain entity for driver location updates.
 * Represents a single point-in-time driver location with speed, heading, and timestamp.
 * Persisted to PostgreSQL for audit trail after batching from Redis.
 */
@Entity
@Table(
    name = "driver_locations",
    indexes = {
        @Index(name = "idx_driver_id_timestamp", columnList = "driver_id, timestamp"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_driver_id", columnList = "driver_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "heading")
    private Integer heading;  // 0-360 degrees, null if unavailable

    @Column(name = "speed")
    private Double speed;  // m/s, null if unavailable

    @Column(name = "accuracy")
    private Double accuracy;  // GPS accuracy in meters

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "source")
    private String source;  // "gps", "network", "fused"

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
