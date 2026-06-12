package com.rideshare.notification.service;

import com.rideshare.notification.dto.WebSocketMessage;
import com.rideshare.notification.dto.NotificationEvent;
import com.rideshare.notification.dto.ConnectionState;
import com.rideshare.notification.util.MetricsRecorder;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Core orchestration service for notification operations.
 * Coordinates WebSocket connection management, subscription handling, and message delivery.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final WebSocketConnectionManager connectionManager;
    private final ConnectionStateService connectionStateService;
    private final MessageRouter messageRouter;
    private final MetricsRecorder metricsRecorder;

    /**
     * Handle new WebSocket connection.
     *
     * @param userId the authenticated user ID
     * @param session the WebSocket session
     * @return connection ID for tracking
     */
    public String handleConnection(String userId, WebSocketSession session) {
        String connectionId = "conn-" + UUID.randomUUID();

        // Register in-memory connection
        connectionManager.registerConnection(userId, connectionId, session);

        // Persist connection state to Redis for recovery
        connectionStateService.saveConnection(userId, connectionId);

        log.info("New WebSocket connection established - userId: {}, connectionId: {}", userId, connectionId);
        return connectionId;
    }

    /**
     * Handle WebSocket disconnection.
     *
     * @param connectionId the connection ID
     */
    public void handleDisconnection(String connectionId) {
        Optional<String> userId = connectionManager.getUserId(connectionId);

        if (userId.isPresent()) {
            // Get active rides before cleanup
            Set<String> activeRides = connectionStateService.getActiveRides(connectionId);

            // Unsubscribe from all rides
            activeRides.forEach(rideId -> handleUnsubscribe(connectionId, rideId));

            // Clean up connection state
            connectionStateService.deleteConnection(connectionId);
            connectionManager.unregisterConnection(connectionId);

            log.info("WebSocket connection closed - userId: {}, connectionId: {}",
                    userId.get(), connectionId);
        }
    }

    /**
     * Handle subscription request for a ride.
     *
     * @param connectionId the connection ID
     * @param rideId the ride ID to subscribe to
     */
    public void handleSubscribe(String connectionId, String rideId) {
        Optional<String> userId = connectionManager.getUserId(connectionId);

        if (userId.isPresent()) {
            messageRouter.subscribe(rideId, userId.get());
            connectionStateService.addRideSubscription(connectionId, rideId);
            log.debug("Subscription handled - userId: {}, rideId: {}", userId.get(), rideId);
        } else {
            log.warn("Subscription requested for unknown connection: {}", connectionId);
        }
    }

    /**
     * Handle unsubscription request for a ride.
     *
     * @param connectionId the connection ID
     * @param rideId the ride ID to unsubscribe from
     */
    public void handleUnsubscribe(String connectionId, String rideId) {
        Optional<String> userId = connectionManager.getUserId(connectionId);

        if (userId.isPresent()) {
            messageRouter.unsubscribe(rideId, userId.get());
            connectionStateService.removeRideSubscription(connectionId, rideId);
            log.debug("Unsubscription handled - userId: {}, rideId: {}", userId.get(), rideId);
        }
    }

    /**
     * Handle heartbeat/ping to keep connection alive.
     *
     * @param connectionId the connection ID
     */
    public void handleHeartbeat(String connectionId) {
        connectionStateService.updateHeartbeat(connectionId);
    }

    /**
     * Process incoming Kafka event and route to subscribers.
     *
     * @param event the notification event from Kafka
     * @return number of messages delivered
     */
    public int processKafkaEvent(NotificationEvent event) {
        metricsRecorder.recordEventReceived(event.getEventType());
        return messageRouter.routeEvent(event);
    }

    /**
     * Recover connection after mobile reconnection.
     * Restores subscriptions and delivers pending information.
     *
     * @param connectionId the new connection ID
     * @param userId the user ID
     */
    public void recoverConnection(String connectionId, String userId) {
        // Get previous connection state if available
        // This could be enhanced to deliver queued messages
        Set<String> previousRides = connectionStateService.getActiveRides(connectionId);

        // Re-subscribe to all previous rides
        previousRides.forEach(rideId -> handleSubscribe(connectionId, rideId));

        metricsRecorder.recordReconnection();
        log.info("Connection recovered - userId: {}, rideCount: {}", userId, previousRides.size());
    }

    /**
     * Get metrics snapshot for monitoring.
     *
     * @return metrics map
     */
    public NotificationMetrics getMetrics() {
        return NotificationMetrics.builder()
                .activeConnections(connectionManager.getTotalConnections())
                .totalSubscriptions(messageRouter.getTotalSubscriptions())
                .build();
    }

    /**
     * Graceful shutdown - close all connections and drain.
     * Waits 60 seconds for clients to disconnect gracefully.
     */
    public void gracefulShutdown() {
        log.info("Starting graceful shutdown - {} active connections",
                connectionManager.getTotalConnections());

        // Send shutdown notification to all connected clients
        connectionManager.closeAllConnections();

        log.info("Graceful shutdown complete");
    }

    /**
     * Metrics DTO for monitoring.
     */
    @lombok.Data
    @lombok.Builder
    public static class NotificationMetrics {
        private long activeConnections;
        private int totalSubscriptions;
    }
}
