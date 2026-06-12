package com.rideshare.notification.service;

import com.rideshare.notification.dto.NotificationEvent;
import com.rideshare.notification.util.MetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 * Tests orchestration of connection management, subscriptions, and event processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    private NotificationService notificationService;

    @Mock
    private WebSocketConnectionManager connectionManager;

    @Mock
    private ConnectionStateService connectionStateService;

    @Mock
    private MessageRouter messageRouter;

    @Mock
    private MetricsRecorder metricsRecorder;

    @Mock
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                connectionManager,
                connectionStateService,
                messageRouter,
                metricsRecorder
        );
    }

    @Test
    @DisplayName("should_handle_new_connection_and_return_connection_id")
    void should_handle_new_connection_and_return_connection_id() {
        String userId = "user-123";

        String connectionId = notificationService.handleConnection(userId, session);

        assertNotNull(connectionId);
        assertTrue(connectionId.startsWith("conn-"));
        verify(connectionManager).registerConnection(eq(userId), eq(connectionId), eq(session));
        verify(connectionStateService).saveConnection(eq(userId), eq(connectionId));
    }

    @Test
    @DisplayName("should_handle_disconnection_and_cleanup_subscriptions")
    void should_handle_disconnection_and_cleanup_subscriptions() {
        String connectionId = "conn-123";
        String userId = "user-123";
        String rideId = "ride-456";

        when(connectionManager.getUserId(connectionId)).thenReturn(java.util.Optional.of(userId));
        when(connectionStateService.getActiveRides(connectionId))
                .thenReturn(java.util.Set.of(rideId));

        notificationService.handleDisconnection(connectionId);

        verify(messageRouter).unsubscribe(eq(rideId), eq(userId));
        verify(connectionStateService).deleteConnection(connectionId);
        verify(connectionManager).unregisterConnection(connectionId);
    }

    @Test
    @DisplayName("should_handle_subscribe_request")
    void should_handle_subscribe_request() {
        String connectionId = "conn-123";
        String userId = "user-123";
        String rideId = "ride-456";

        when(connectionManager.getUserId(connectionId)).thenReturn(java.util.Optional.of(userId));

        notificationService.handleSubscribe(connectionId, rideId);

        verify(messageRouter).subscribe(eq(rideId), eq(userId));
        verify(connectionStateService).addRideSubscription(eq(connectionId), eq(rideId));
    }

    @Test
    @DisplayName("should_handle_unsubscribe_request")
    void should_handle_unsubscribe_request() {
        String connectionId = "conn-123";
        String userId = "user-123";
        String rideId = "ride-456";

        when(connectionManager.getUserId(connectionId)).thenReturn(java.util.Optional.of(userId));

        notificationService.handleUnsubscribe(connectionId, rideId);

        verify(messageRouter).unsubscribe(eq(rideId), eq(userId));
        verify(connectionStateService).removeRideSubscription(eq(connectionId), eq(rideId));
    }

    @Test
    @DisplayName("should_handle_heartbeat_and_update_connection_state")
    void should_handle_heartbeat_and_update_connection_state() {
        String connectionId = "conn-123";

        notificationService.handleHeartbeat(connectionId);

        verify(connectionStateService).updateHeartbeat(connectionId);
    }

    @Test
    @DisplayName("should_process_kafka_event_and_route_to_subscribers")
    void should_process_kafka_event_and_route_to_subscribers() {
        NotificationEvent event = NotificationEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .eventType("driver.location_updated")
                .rideId("ride-123")
                .userId("user-456")
                .timestamp(Instant.now())
                .data(null)
                .build();

        when(messageRouter.routeEvent(event)).thenReturn(2);

        int deliveryCount = notificationService.processKafkaEvent(event);

        assertEquals(2, deliveryCount);
        verify(metricsRecorder).recordEventReceived("driver.location_updated");
        verify(messageRouter).routeEvent(event);
    }

    @Test
    @DisplayName("should_recover_connection_and_restore_subscriptions")
    void should_recover_connection_and_restore_subscriptions() {
        String connectionId = "conn-123";
        String userId = "user-456";
        String rideId1 = "ride-1";
        String rideId2 = "ride-2";

        when(connectionStateService.getActiveRides(connectionId))
                .thenReturn(java.util.Set.of(rideId1, rideId2));
        when(connectionManager.getUserId(connectionId))
                .thenReturn(java.util.Optional.of(userId));

        notificationService.recoverConnection(connectionId, userId);

        verify(messageRouter, times(2)).subscribe(anyString(), eq(userId));
        verify(metricsRecorder).recordReconnection();
    }

    @Test
    @DisplayName("should_get_metrics_snapshot")
    void should_get_metrics_snapshot() {
        when(connectionManager.getTotalConnections()).thenReturn(5L);
        when(messageRouter.getTotalSubscriptions()).thenReturn(12);

        NotificationService.NotificationMetrics metrics = notificationService.getMetrics();

        assertNotNull(metrics);
        assertEquals(5L, metrics.getActiveConnections());
        assertEquals(12, metrics.getTotalSubscriptions());
    }

    @Test
    @DisplayName("should_perform_graceful_shutdown")
    void should_perform_graceful_shutdown() {
        notificationService.gracefulShutdown();

        verify(connectionManager).closeAllConnections();
    }

    @Test
    @DisplayName("should_handle_disconnection_without_user_id")
    void should_handle_disconnection_without_user_id() {
        String connectionId = "conn-123";

        when(connectionManager.getUserId(connectionId)).thenReturn(java.util.Optional.empty());

        notificationService.handleDisconnection(connectionId);

        verify(connectionStateService, never()).deleteConnection(any());
        verify(connectionManager, never()).unregisterConnection(any());
    }

    @Test
    @DisplayName("should_handle_subscribe_with_unknown_connection")
    void should_handle_subscribe_with_unknown_connection() {
        String connectionId = "conn-unknown";
        String rideId = "ride-456";

        when(connectionManager.getUserId(connectionId)).thenReturn(java.util.Optional.empty());

        notificationService.handleSubscribe(connectionId, rideId);

        verify(messageRouter, never()).subscribe(any(), any());
    }
}
