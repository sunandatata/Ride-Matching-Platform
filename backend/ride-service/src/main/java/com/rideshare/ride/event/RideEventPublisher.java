package com.rideshare.ride.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.entity.RideStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes ride events to Kafka for event streaming and real-time updates.
 * Events are consumed by Location Service, Notification Service, Analytics, etc.
 * Implements fire-and-forget with logging for failures.
 */
@Component
@Slf4j
public class RideEventPublisher {

    private static final String RIDE_REQUESTED_TOPIC = "ride.requested";
    private static final String RIDE_MATCHED_TOPIC = "ride.matched";
    private static final String RIDE_ACCEPTED_TOPIC = "ride.accepted";
    private static final String RIDE_ARRIVED_TOPIC = "ride.arrived";
    private static final String RIDE_STARTED_TOPIC = "ride.started";
    private static final String RIDE_COMPLETED_TOPIC = "ride.completed";
    private static final String RIDE_CANCELLED_TOPIC = "ride.cancelled";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public RideEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a ride.requested event when a new ride is created.
     *
     * @param ride the newly created ride
     */
    public void publishRideRequested(Ride ride) {
        Map<String, Object> event = new HashMap<>();
        event.put("ride_id", ride.getId());
        event.put("rider_id", ride.getRiderId());
        event.put("pickup_latitude", ride.getPickupLatitude());
        event.put("pickup_longitude", ride.getPickupLongitude());
        event.put("dropoff_latitude", ride.getDropoffLatitude());
        event.put("dropoff_longitude", ride.getDropoffLongitude());
        event.put("passenger_count", ride.getPassengerCount());
        event.put("created_at", ride.getCreatedAt());
        publishEvent(RIDE_REQUESTED_TOPIC, ride.getId(), event);
    }

    /**
     * Publishes a ride.matched event when the Matching Engine assigns a driver.
     *
     * @param ride the ride with assigned driver
     */
    public void publishRideMatched(Ride ride) {
        Map<String, Object> event = new HashMap<>();
        event.put("ride_id", ride.getId());
        event.put("driver_id", ride.getDriverId());
        event.put("estimated_fare", ride.getEstimatedFare());
        event.put("estimated_duration_seconds", ride.getEstimatedDurationSeconds());
        event.put("driver_eta", ride.getDriverETA());
        event.put("matched_at", ride.getMatchedAt());
        publishEvent(RIDE_MATCHED_TOPIC, ride.getId(), event);
    }

    /**
     * Publishes a ride.accepted event when the driver confirms the match.
     *
     * @param ride the accepted ride
     */
    public void publishRideAccepted(Ride ride) {
        Map<String, Object> event = new HashMap<>();
        event.put("ride_id", ride.getId());
        event.put("driver_id", ride.getDriverId());
        event.put("accepted_at", ride.getAcceptedAt());
        publishEvent(RIDE_ACCEPTED_TOPIC, ride.getId(), event);
    }

    /**
     * Publishes a ride.arrived event when the driver reaches pickup location.
     *
     * @param ride the ride where driver has arrived
     */
    public void publishRideArrived(Ride ride) {
        Map<String, Object> event = new HashMap<>();
        event.put("ride_id", ride.getId());
        event.put("driver_id", ride.getDriverId());
        event.put("arrived_at", ride.getArrivedAt());
        publishEvent(RIDE_ARRIVED_TOPIC, ride.getId(), event);
    }

    /**
     * Publishes a ride.started event when the ride begins (rider boarded).
     *
     * @param ride the started ride
     */
    public void publishRideStarted(Ride ride) {
        Map<String, Object> event = new HashMap<>();
        event.put("ride_id", ride.getId());
        event.put("rider_id", ride.getRiderId());
        event.put("driver_id", ride.getDriverId());
        event.put("started_at", ride.getStartedAt());
        publishEvent(RIDE_STARTED_TOPIC, ride.getId(), event);
    }

    /**
     * Publishes a ride.completed event when the ride finishes.
     *
     * @param ride the completed ride
     */
    public void publishRideCompleted(Ride ride) {
        Map<String, Object> event = new HashMap<>();
        event.put("ride_id", ride.getId());
        event.put("rider_id", ride.getRiderId());
        event.put("driver_id", ride.getDriverId());
        event.put("actual_fare", ride.getActualFare());
        event.put("actual_duration_seconds", ride.getActualDurationSeconds());
        event.put("completed_at", ride.getCompletedAt());
        publishEvent(RIDE_COMPLETED_TOPIC, ride.getId(), event);
    }

    /**
     * Publishes a ride.cancelled event when a ride is cancelled.
     *
     * @param ride the cancelled ride
     */
    public void publishRideCancelled(Ride ride) {
        Map<String, Object> event = new HashMap<>();
        event.put("ride_id", ride.getId());
        event.put("reason", ride.getCancellationReason());
        event.put("initiated_by", ride.getCancellationInitiator());
        event.put("cancelled_at", ride.getCancelledAt());
        publishEvent(RIDE_CANCELLED_TOPIC, ride.getId(), event);
    }

    /**
     * Generic method to publish an event to Kafka.
     * Uses fire-and-forget pattern with callback logging.
     *
     * @param topic the Kafka topic
     * @param key the message key (used for partitioning)
     * @param event the event payload as a map
     */
    private void publishEvent(String topic, String key, Map<String, Object> event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage(), ex);
                    } else {
                        log.debug("Event published successfully to topic {} with key {}", topic, key);
                    }
                });
        } catch (Exception e) {
            log.error("Error serializing event for topic {}: {}", topic, e.getMessage(), e);
        }
    }
}
