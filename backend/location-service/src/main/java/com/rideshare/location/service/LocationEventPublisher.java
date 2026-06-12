package com.rideshare.location.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.location.event.LocationChangedEvent;
import com.rideshare.location.model.LocationUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;

/**
 * Publishes location.changed events to Kafka.
 * Consumed by Matching Engine, Notification Service, Analytics.
 * Non-blocking, fire-and-forget semantics.
 */
@Slf4j
@Service
public class LocationEventPublisher {

    private static final String LOCATION_CHANGED_TOPIC = "location.changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LocationEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a location.changed event.
     * Asynchronous, non-blocking operation.
     *
     * @param locationUpdate Location update entity
     */
    public void publishLocationChanged(LocationUpdate locationUpdate) {
        try {
            LocationChangedEvent event = LocationChangedEvent.builder()
                .driverId(locationUpdate.getDriverId())
                .latitude(locationUpdate.getLatitude().doubleValue())
                .longitude(locationUpdate.getLongitude().doubleValue())
                .heading(locationUpdate.getHeading())
                .speed(locationUpdate.getSpeed())
                .timestamp(locationUpdate.getTimestamp())
                .source(locationUpdate.getSource())
                .eventPublishedAt(Instant.now())
                .build();

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(LOCATION_CHANGED_TOPIC, locationUpdate.getDriverId(), eventJson);

            log.debug("Published location.changed event for driver {}",
                     locationUpdate.getDriverId());

        } catch (Exception e) {
            log.error("Failed to publish location.changed event for driver {}",
                     locationUpdate.getDriverId(), e);
            // Non-critical error, don't throw
        }
    }
}
