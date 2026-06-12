package com.rideshare.location.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Driver online/offline status tracking.
 * Lightweight entity for current driver status.
 */
@Entity
@Table(name = "driver_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverStatus {

    @Id
    @Column(name = "driver_id")
    private String driverId;

    @Column(name = "is_online", nullable = false)
    private Boolean isOnline;

    @Column(name = "last_location_update")
    private Instant lastLocationUpdate;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
