package com.rideshare.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service - Real-time WebSocket and Push Notification Platform.
 *
 * Provides:
 * - WebSocket connections for real-time ride updates
 * - Kafka event consumption and routing
 * - Redis-backed connection state persistence
 * - Message deduplication and ordering
 * - Graceful shutdown with connection draining
 * - Mobile reconnection support
 *
 * WebSocket Endpoint: ws://localhost:8081/ws
 *
 * Message Types:
 * - subscribe: Subscribe to ride updates
 * - unsubscribe: Unsubscribe from ride updates
 * - ping: Keep-alive heartbeat
 *
 * Kafka Topics Consumed:
 * - ride.matched
 * - ride.status_changed
 * - location.changed
 * - eta.updated
 * - ride.cancelled
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
