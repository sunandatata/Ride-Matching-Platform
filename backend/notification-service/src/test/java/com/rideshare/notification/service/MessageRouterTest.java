package com.rideshare.notification.service;

import com.rideshare.notification.dto.NotificationEvent;
import com.rideshare.notification.util.MessageDeduplicator;
import com.rideshare.notification.util.MetricsRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageRouter.
 * Tests subscription management and event routing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageRouter")
class MessageRouterTest {

    private MessageRouter messageRouter;

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Mock
    private MessageDeduplicator deduplicator;

    @Mock
    private MetricsRecorder metricsRecorder;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        messageRouter = new MessageRouter(
                connectionManager,
                deduplicator,
                metricsRecorder,
                objectMapper
        );
    }

    @Test
    @DisplayName("should_subscribe_user_to_ride")
    void should_subscribe_user_to_ride() {
        String rideId = "ride-123";
        String userId = "user-456";

        messageRouter.subscribe(rideId, userId);

        java.util.Set<String> subscribers = messageRouter.getSubscribers(rideId);
        assertTrue(subscribers.contains(userId));
    }

    @Test
    @DisplayName("should_unsubscribe_user_from_ride")
    void should_unsubscribe_user_from_ride() {
        String rideId = "ride-123";
        String userId = "user-456";

        messageRouter.subscribe(rideId, userId);
        assertEquals(1, messageRouter.getSubscriptionCount(rideId));

        messageRouter.unsubscribe(rideId, userId);

        assertEquals(0, messageRouter.getSubscriptionCount(rideId));
    }

    @Test
    @DisplayName("should_get_all_subscribers_for_ride")
    void should_get_all_subscribers_for_ride() {
        String rideId = "ride-123";
        String user1 = "user-1";
        String user2 = "user-2";

        messageRouter.subscribe(rideId, user1);
        messageRouter.subscribe(rideId, user2);

        java.util.Set<String> subscribers = messageRouter.getSubscribers(rideId);

        assertEquals(2, subscribers.size());
        assertTrue(subscribers.contains(user1));
        assertTrue(subscribers.contains(user2));
    }

    @Test
    @DisplayName("should_route_event_and_deliver_to_subscribers")
    void should_route_event_and_deliver_to_subscribers() {
        String rideId = "ride-123";
        String userId1 = "user-1";
        String userId2 = "user-2";

        messageRouter.subscribe(rideId, userId1);
        messageRouter.subscribe(rideId, userId2);

        when(deduplicator.isNewMessage(rideId, "msg-1")).thenReturn(true);
        when(connectionManager.getUserSessions(userId1)).thenReturn(java.util.List.of(session1));
        when(connectionManager.getUserSessions(userId2)).thenReturn(java.util.List.of(session2));
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);

        NotificationEvent event = NotificationEvent.builder()
                .messageId("msg-1")
                .eventType("driver.location_updated")
                .rideId(rideId)
                .userId(userId1)
                .timestamp(Instant.now())
                .build();

        int deliveryCount = messageRouter.routeEvent(event);

        assertTrue(deliveryCount > 0);
        verify(metricsRecorder).recordMessageDelivered("driver.location_updated");
    }

    @Test
    @DisplayName("should_detect_duplicate_messages")
    void should_detect_duplicate_messages() {
        String rideId = "ride-123";
        String userId = "user-456";
        String messageId = "msg-duplicate";

        messageRouter.subscribe(rideId, userId);
        when(deduplicator.isNewMessage(rideId, messageId))
                .thenReturn(false);

        NotificationEvent event = NotificationEvent.builder()
                .messageId(messageId)
                .eventType("driver.location_updated")
                .rideId(rideId)
                .timestamp(Instant.now())
                .build();

        int deliveryCount = messageRouter.routeEvent(event);

        assertEquals(0, deliveryCount);
        verify(metricsRecorder).recordDuplicateMessageDetected();
    }

    @Test
    @DisplayName("should_clear_ride_subscriptions")
    void should_clear_ride_subscriptions() {
        String rideId = "ride-123";
        String userId = "user-456";

        messageRouter.subscribe(rideId, userId);
        assertEquals(1, messageRouter.getSubscriptionCount(rideId));

        messageRouter.clearRideSubscriptions(rideId);

        assertEquals(0, messageRouter.getSubscriptionCount(rideId));
        verify(deduplicator).clearRideState(rideId);
    }

    @Test
    @DisplayName("should_get_total_subscriptions")
    void should_get_total_subscriptions() {
        messageRouter.subscribe("ride-1", "user-1");
        messageRouter.subscribe("ride-1", "user-2");
        messageRouter.subscribe("ride-2", "user-3");

        int totalSubscriptions = messageRouter.getTotalSubscriptions();

        assertEquals(3, totalSubscriptions);
    }

    @Test
    @DisplayName("should_handle_event_with_no_subscribers")
    void should_handle_event_with_no_subscribers() {
        NotificationEvent event = NotificationEvent.builder()
                .messageId("msg-1")
                .eventType("driver.location_updated")
                .rideId("ride-no-subs")
                .timestamp(Instant.now())
                .build();

        when(deduplicator.isNewMessage("ride-no-subs", "msg-1")).thenReturn(true);

        int deliveryCount = messageRouter.routeEvent(event);

        assertEquals(0, deliveryCount);
        verify(metricsRecorder).recordMessageDelivered("driver.location_updated");
    }
}
