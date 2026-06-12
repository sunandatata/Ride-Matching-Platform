package com.rideshare.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.matching.dto.MatchCandidate;
import com.rideshare.matching.dto.RideRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core matching algorithm
 *
 * Consumes: RideRequested events from Kafka
 * Produces: RideMatched events to Kafka
 *
 * Algorithm:
 * 1. Get nearby drivers from location service (or Redis Geo)
 * 2. Filter: online, available, correct vehicle type
 * 3. Enrich: Get ratings, acceptance rates from driver service
 * 4. Score: Rating (40%) + Acceptance Rate (30%) + ETA inverse (30%)
 * 5. Assign: Top-scored driver
 * 6. Publish: RideMatched event
 */
@Service
@Slf4j
public class MatchingService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public MatchingService(KafkaTemplate<String, String> kafkaTemplate,
                          RestTemplate restTemplate,
                          ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Listen for ride requests and match them with drivers
     */
    @KafkaListener(
            topics = "ride-requested",
            groupId = "matching-engine",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void matchRide(String messageJson) {
        try {
            log.info("Processing ride request: {}", messageJson);
            RideRequestedEvent event = objectMapper.readValue(messageJson, RideRequestedEvent.class);

            // Step 1: Find nearby drivers (hardcoded for demo)
            List<DriverInfo> nearbyDrivers = getNearbyDrivers(
                event.getPickupLat(),
                event.getPickupLng(),
                5.0  // 5 km radius
            );

            if (nearbyDrivers.isEmpty()) {
                log.warn("No drivers available for ride: {}", event.getRideId());
                publishNoDriverAvailable(event.getRideId());
                return;
            }

            // Step 2: Enrich with ratings and availability
            List<DriverInfo> candidateDrivers = nearbyDrivers.stream()
                    .filter(DriverInfo::isOnline)
                    .filter(DriverInfo::isAvailable)
                    .collect(Collectors.toList());

            if (candidateDrivers.isEmpty()) {
                log.warn("No available drivers for ride: {}", event.getRideId());
                publishNoDriverAvailable(event.getRideId());
                return;
            }

            // Step 3: Score each driver
            List<MatchCandidate> scored = candidateDrivers.stream()
                    .map(driver -> scoreDriver(driver, event))
                    .sorted(Comparator.comparingDouble(MatchCandidate::getScore).reversed())
                    .collect(Collectors.toList());

            // Step 4: Assign top driver
            MatchCandidate bestMatch = scored.get(0);
            log.info("Matched ride {} to driver {} with score {}",
                event.getRideId(), bestMatch.getDriverId(), bestMatch.getScore());

            // Step 5: Call ride-service to update status
            assignDriverToRide(event.getRideId(), bestMatch.getDriverId(), bestMatch.getEta());

            // Step 6: Publish RideMatched event
            publishRideMatched(event, bestMatch);

        } catch (Exception e) {
            log.error("Error matching ride", e);
        }
    }

    /**
     * Score a driver based on: rating (40%), acceptance rate (30%), ETA (30%)
     */
    private MatchCandidate scoreDriver(DriverInfo driver, RideRequestedEvent ride) {
        // Rating score: 0-1 (5 stars = 1.0)
        double ratingScore = driver.getRating() / 5.0;

        // Acceptance rate: 0-1
        double acceptanceScore = driver.getAcceptanceRate() / 100.0;

        // ETA inverse: lower ETA = higher score
        // If ETA is 300 seconds (5 min), score = 0.25
        // If ETA is 60 seconds (1 min), score = 0.63
        double etaScore = 1.0 / (1.0 + (driver.getEta() / 100.0));

        // Weighted score
        double finalScore = (ratingScore * 0.4) + (acceptanceScore * 0.3) + (etaScore * 0.3);

        return MatchCandidate.builder()
                .driverId(driver.getDriverId())
                .score(finalScore)
                .eta(driver.getEta())
                .rating(driver.getRating())
                .acceptanceRate(driver.getAcceptanceRate())
                .build();
    }

    /**
     * Get nearby drivers within radius
     * For demo: return mock drivers
     * For production: query Redis Geo or location service
     */
    private List<DriverInfo> getNearbyDrivers(Double lat, Double lng, Double radiusKm) {
        // DEMO: Return mock drivers
        // In production: GEOSEARCH driver_locations BY RADIUS {radiusKm} km
        return List.of(
                new DriverInfo("DRIVER-001", "John Smith", 4.8, 95.0, 150, true, true),
                new DriverInfo("DRIVER-002", "Jane Doe", 4.6, 92.0, 180, true, true),
                new DriverInfo("DRIVER-003", "Bob Johnson", 4.9, 98.0, 120, true, true),
                new DriverInfo("DRIVER-004", "Alice Brown", 4.7, 90.0, 200, true, true)
        );
    }

    /**
     * Call ride-service to assign driver
     */
    private void assignDriverToRide(String rideId, String driverId, Integer eta) {
        try {
            String url = "http://localhost:8081/api/v1/rides/" + rideId + "/driver";
            String payload = String.format("{\"driverId\":\"%s\",\"eta\":%d}", driverId, eta);

            log.info("Calling ride-service to assign driver: PUT {}", url);
            restTemplate.put(url, payload);

        } catch (Exception e) {
            log.error("Failed to assign driver to ride", e);
        }
    }

    /**
     * Publish RideMatched event to Kafka
     */
    private void publishRideMatched(RideRequestedEvent ride, MatchCandidate bestMatch) {
        try {
            String message = String.format(
                    "{\"rideId\":\"%s\",\"driverId\":\"%s\",\"matchedAt\":%d,\"eta\":%d}",
                    ride.getRideId(), bestMatch.getDriverId(), System.currentTimeMillis(), bestMatch.getEta()
            );

            kafkaTemplate.send("ride-matched", ride.getRideId(), message);
            log.info("Published RideMatched event for ride: {}", ride.getRideId());

        } catch (Exception e) {
            log.error("Failed to publish RideMatched event", e);
        }
    }

    /**
     * Publish event when no drivers available
     */
    private void publishNoDriverAvailable(String rideId) {
        try {
            String message = String.format(
                    "{\"rideId\":\"%s\",\"reason\":\"no_drivers_available\",\"timestamp\":%d}",
                    rideId, System.currentTimeMillis()
            );
            kafkaTemplate.send("ride-no-driver", rideId, message);

        } catch (Exception e) {
            log.error("Failed to publish no-driver event", e);
        }
    }

    /**
     * Internal DTO for driver info
     */
    public static class DriverInfo {
        public String driverId;
        public String name;
        public Double rating;        // 1-5
        public Double acceptanceRate; // 0-100
        public Integer eta;          // seconds
        public Boolean online;
        public Boolean available;

        public DriverInfo(String driverId, String name, Double rating, Double acceptanceRate,
                         Integer eta, Boolean online, Boolean available) {
            this.driverId = driverId;
            this.name = name;
            this.rating = rating;
            this.acceptanceRate = acceptanceRate;
            this.eta = eta;
            this.online = online;
            this.available = available;
        }

        // Getters
        public String getDriverId() { return driverId; }
        public Double getRating() { return rating; }
        public Double getAcceptanceRate() { return acceptanceRate; }
        public Integer getEta() { return eta; }
        public Boolean isOnline() { return online; }
        public Boolean isAvailable() { return available; }
    }
}
