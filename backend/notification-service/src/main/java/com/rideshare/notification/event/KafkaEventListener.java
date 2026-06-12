package com.rideshare.notification.event;

import com.rideshare.notification.dto.NotificationEvent;
import com.rideshare.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes Kafka events and routes them to WebSocket subscribers.
 * Listens to ride, location, and ETA update topics.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaEventListener {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Listen to ride.matched events from Kafka.
     * Notifies both rider and driver when matched.
     *
     * @param messageJson the Kafka message as JSON
     */
    @KafkaListener(
            topics = "ride.matched",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRideMatched(String messageJson) {
        processEvent(messageJson, "ride.matched");
    }

    /**
     * Listen to ride.status_changed events from Kafka.
     * Notifies both rider and driver of status changes (accepted, arriving, started, completed).
     *
     * @param messageJson the Kafka message as JSON
     */
    @KafkaListener(
            topics = "ride.status_changed",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRideStatusChanged(String messageJson) {
        processEvent(messageJson, "ride.status_changed");
    }

    /**
     * Listen to location.changed events from Kafka.
     * Sends driver location updates to subscribed riders.
     *
     * @param messageJson the Kafka message as JSON
     */
    @KafkaListener(
            topics = "location.changed",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onLocationChanged(String messageJson) {
        processEvent(messageJson, "driver.location_updated");
    }

    /**
     * Listen to eta.updated events from Kafka.
     * Sends ETA updates to subscribed riders.
     *
     * @param messageJson the Kafka message as JSON
     */
    @KafkaListener(
            topics = "eta.updated",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEtaUpdated(String messageJson) {
        processEvent(messageJson, "eta_updated");
    }

    /**
     * Listen to ride.cancelled events from Kafka.
     * Notifies both rider and driver when ride is cancelled.
     *
     * @param messageJson the Kafka message as JSON
     */
    @KafkaListener(
            topics = "ride.cancelled",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRideCancelled(String messageJson) {
        processEvent(messageJson, "ride.cancelled");
    }

    /**
     * Process and route a Kafka event to WebSocket subscribers.
     *
     * @param messageJson the message JSON
     * @param eventType the notification event type
     */
    private void processEvent(String messageJson, String eventType) {
        try {
            NotificationEvent event = objectMapper.readValue(messageJson, NotificationEvent.class);
            event.setEventType(eventType);

            int deliveryCount = notificationService.processKafkaEvent(event);
            log.debug("Processed Kafka event - type: {}, ride: {}, delivered: {}",
                    eventType, event.getRideId(), deliveryCount);
        } catch (Exception e) {
            log.error("Failed to process Kafka event - type: {}, message: {}",
                    eventType, messageJson, e);
        }
    }
}
