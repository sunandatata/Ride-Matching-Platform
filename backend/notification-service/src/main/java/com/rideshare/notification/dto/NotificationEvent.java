package com.rideshare.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Internal event representation for notifications.
 * Represents events received from Kafka and routed to WebSocket subscribers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("ride_id")
    private String rideId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("data")
    private Object data;

    /**
     * Get the sequence number from message ID for ordering purposes.
     *
     * @return sequence number if available, Long.MAX_VALUE otherwise
     */
    public long getSequenceNumber() {
        if (messageId == null || !messageId.contains("-")) {
            return Long.MAX_VALUE;
        }
        try {
            String[] parts = messageId.split("-");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
