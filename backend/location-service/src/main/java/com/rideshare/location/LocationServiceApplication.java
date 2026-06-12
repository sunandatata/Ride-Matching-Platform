package com.rideshare.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Location Service Application.
 * High-throughput microservice handling 100k+ location updates/sec.
 *
 * Key responsibilities:
 * - Location ingestion (PUT endpoint)
 * - Batching and pipelined Redis writes
 * - Async PostgreSQL persistence
 * - Event publishing for location changes
 * - Spatial queries for nearby drivers
 *
 * Technology Stack:
 * - Spring Boot 3
 * - Redis for Geo-spatial indexing
 * - PostgreSQL for audit trail
 * - Kafka for event publishing
 */
@SpringBootApplication
public class LocationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocationServiceApplication.class, args);
    }
}
