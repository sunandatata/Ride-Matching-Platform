package com.rideshare.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * WebSocket message contract for client-server communication.
 * Supports subscribe, unsubscribe, and ping messages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessage {

    @NotBlank(message = "Message type cannot be blank")
    private String type;

    @JsonProperty("ride_id")
    private String rideId;

    @JsonProperty("message_id")
    private String messageId;

    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    private Object data;

    /**
     * Factory method to create a subscription message.
     *
     * @param rideId the ride ID to subscribe to
     * @return WebSocketMessage configured for subscription
     */
    public static WebSocketMessage subscribe(String rideId) {
        return WebSocketMessage.builder()
                .type("subscribe")
                .rideId(rideId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Factory method to create an unsubscription message.
     *
     * @param rideId the ride ID to unsubscribe from
     * @return WebSocketMessage configured for unsubscription
     */
    public static WebSocketMessage unsubscribe(String rideId) {
        return WebSocketMessage.builder()
                .type("unsubscribe")
                .rideId(rideId)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Factory method to create a ping message.
     *
     * @return WebSocketMessage configured for ping
     */
    public static WebSocketMessage ping() {
        return WebSocketMessage.builder()
                .type("ping")
                .timestamp(Instant.now())
                .build();
    }
}
