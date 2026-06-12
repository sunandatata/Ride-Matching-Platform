package com.rideshare.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Set;

/**
 * Connection state persisted in Redis for session recovery.
 * Enables mobile reconnection to different Notification Service instances.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectionState {

    @JsonProperty("connection_id")
    private String connectionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("instance_id")
    private String instanceId;

    @JsonProperty("active_rides")
    private Set<String> activeRides;

    @JsonProperty("connected_at")
    private Instant connectedAt;

    @JsonProperty("last_heartbeat")
    private Instant lastHeartbeat;

    /**
     * Update the last heartbeat timestamp to current time.
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    /**
     * Check if this connection has expired (no heartbeat for 30 minutes).
     *
     * @return true if connection has expired
     */
    @JsonIgnore
    public boolean isExpired() {
        if (lastHeartbeat == null) {
            return false;
        }
        return Instant.now().getEpochSecond() - lastHeartbeat.getEpochSecond() > 1800; // 30 minutes
    }
}
