package com.rideshare.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Matching Engine - Assigns drivers to ride requests
 *
 * Consumes: RideRequested event from Kafka
 * Produces: RideMatched event to Kafka
 *
 * Responsibility: Find best driver for a ride within <200ms
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.rideshare.matching", "com.rideshare.shared"})
public class MatchingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingEngineApplication.class, args);
    }
}
