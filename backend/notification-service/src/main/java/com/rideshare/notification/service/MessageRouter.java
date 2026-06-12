package com.rideshare.notification.service;

import com.rideshare.notification.dto.NotificationEvent;
import com.rideshare.notification.util.MessageDeduplicator;
import com.rideshare.notification.util.MetricsRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Routes Kafka events to WebSocket subscribers.
 * Handles message deduplication, ordering, and delivery to relevant users.
 */
@Service
@Slf4j
public class MessageRouter {

    // Map: rideId -> Set<userId> (subscribers)
    private final Map<String, Set<String>> rideSubscriptions = new HashMap<>();

    private final WebSocketConnectionManager connectionManager;
    private final MessageDeduplicator deduplicator;
    private final MetricsRecorder metricsRecorder;
    private final ObjectMapper objectMapper;

    public MessageRouter(
            WebSocketConnectionManager connectionManager,
            MessageDeduplicator deduplicator,
            MetricsRecorder metricsRecorder,
            ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.deduplicator = deduplicator;
        this.metricsRecorder = metricsRecorder;
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    /**
     * Subscribe a user to ride updates.
     *
     * @param rideId the ride ID
     * @param userId the user ID
     */
    public synchronized void subscribe(String rideId, String userId) {
        rideSubscriptions.computeIfAbsent(rideId, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(userId);
        log.debug("User subscribed to ride - user: {}, ride: {}", userId, rideId);
    }

    /**
     * Unsubscribe a user from ride updates.
     *
     * @param rideId the ride ID
     * @param userId the user ID
     */
    public synchronized void unsubscribe(String rideId, String userId) {
        Set<String> subscribers = rideSubscriptions.get(rideId);
        if (subscribers != null) {
            subscribers.remove(userId);
            if (subscribers.isEmpty()) {
                rideSubscriptions.remove(rideId);
            }
        }
        log.debug("User unsubscribed from ride - user: {}, ride: {}", userId, rideId);
    }

    /**
     * Get all subscribers for a ride.
     *
     * @param rideId the ride ID
     * @return Set of user IDs subscribed to this ride
     */
    public Set<String> getSubscribers(String rideId) {
        Set<String> subscribers = rideSubscriptions.get(rideId);
        return subscribers != null ? new HashSet<>(subscribers) : new HashSet<>();
    }

    /**
     * Route an event to all subscribers of the ride.
     * Handles deduplication and ordering.
     *
     * @param event the notification event
     * @return number of successful deliveries
     */
    public int routeEvent(NotificationEvent event) {
        // Check for duplicates
        if (!deduplicator.isNewMessage(event.getRideId(), event.getMessageId())) {
            log.debug("Duplicate message detected - messageId: {}, ride: {}",
                    event.getMessageId(), event.getRideId());
            metricsRecorder.recordDuplicateMessageDetected();
            return 0;
        }

        Set<String> subscribers = getSubscribers(event.getRideId());
        int successCount = 0;

        for (String userId : subscribers) {
            Collection<WebSocketSession> sessions = connectionManager.getUserSessions(userId);
            for (WebSocketSession session : sessions) {
                if (deliverMessage(session, event)) {
                    successCount++;
                }
            }
        }

        metricsRecorder.recordMessageDelivered(event.getEventType());
        log.debug("Routed event to {} subscribers - eventType: {}, ride: {}",
                successCount, event.getEventType(), event.getRideId());

        return successCount;
    }

    /**
     * Deliver a message to a specific WebSocket session.
     *
     * @param session the WebSocket session
     * @param event the notification event
     * @return true if delivery was successful
     */
    private boolean deliverMessage(WebSocketSession session, NotificationEvent event) {
        if (!session.isOpen()) {
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();
            String messageJson = objectMapper.writeValueAsString(event);
            session.sendMessage(new TextMessage(messageJson));
            long deliveryLatency = System.currentTimeMillis() - startTime;
            metricsRecorder.recordMessageDeliveryLatency(deliveryLatency);
            return true;
        } catch (Exception e) {
            log.warn("Failed to deliver message to session: {}", session.getId(), e);
            return false;
        }
    }

    /**
     * Get subscription count for a ride.
     *
     * @param rideId the ride ID
     * @return number of subscribers for this ride
     */
    public int getSubscriptionCount(String rideId) {
        return getSubscribers(rideId).size();
    }

    /**
     * Clear all subscriptions for a ride.
     * Called when ride is completed or cancelled.
     *
     * @param rideId the ride ID
     */
    public synchronized void clearRideSubscriptions(String rideId) {
        rideSubscriptions.remove(rideId);
        deduplicator.clearRideState(rideId);
        log.debug("Cleared all subscriptions for ride: {}", rideId);
    }

    /**
     * Get total number of active subscriptions.
     *
     * @return total subscription count
     */
    public int getTotalSubscriptions() {
        return rideSubscriptions.values().stream()
                .mapToInt(Set::size)
                .sum();
    }
}
