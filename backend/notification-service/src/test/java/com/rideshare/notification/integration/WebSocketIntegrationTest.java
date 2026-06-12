package com.rideshare.notification.integration;

import com.rideshare.notification.dto.NotificationEvent;
import com.rideshare.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for complete WebSocket notification flow.
 * Tests real connection management, subscription, and message delivery.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("WebSocket Integration Tests")
class WebSocketIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RedisTemplate<String, String> stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clear Redis state
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("should_subscribe_and_receive_notification")
    void should_subscribe_and_receive_notification() throws IOException {
        String userId = "test-user-" + UUID.randomUUID();
        String rideId = "test-ride-" + UUID.randomUUID();

        // Create mock session
        MockWebSocketSession session = new MockWebSocketSession();

        // Handle connection
        String connectionId = notificationService.handleConnection(userId, session);
        assertNotNull(connectionId);

        // Subscribe to ride
        notificationService.handleSubscribe(connectionId, rideId);

        // Send event
        NotificationEvent event = NotificationEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .eventType("driver.location_updated")
                .rideId(rideId)
                .userId(userId)
                .timestamp(Instant.now())
                .data(null)
                .build();

        int deliveryCount = notificationService.processKafkaEvent(event);

        assertTrue(deliveryCount > 0);
        assertFalse(session.getSentMessages().isEmpty());
    }

    @Test
    @DisplayName("should_handle_multiple_subscribers_for_same_ride")
    void should_handle_multiple_subscribers_for_same_ride() throws IOException {
        String rideId = "test-ride-" + UUID.randomUUID();
        String userId1 = "test-user-1";
        String userId2 = "test-user-2";

        MockWebSocketSession session1 = new MockWebSocketSession();
        MockWebSocketSession session2 = new MockWebSocketSession();

        String connId1 = notificationService.handleConnection(userId1, session1);
        String connId2 = notificationService.handleConnection(userId2, session2);

        notificationService.handleSubscribe(connId1, rideId);
        notificationService.handleSubscribe(connId2, rideId);

        NotificationEvent event = NotificationEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .eventType("ride.status_changed")
                .rideId(rideId)
                .timestamp(Instant.now())
                .build();

        int deliveryCount = notificationService.processKafkaEvent(event);

        assertEquals(2, deliveryCount);
        assertFalse(session1.getSentMessages().isEmpty());
        assertFalse(session2.getSentMessages().isEmpty());
    }

    @Test
    @DisplayName("should_handle_unsubscribe")
    void should_handle_unsubscribe() throws IOException {
        String userId = "test-user-" + UUID.randomUUID();
        String rideId = "test-ride-" + UUID.randomUUID();

        MockWebSocketSession session = new MockWebSocketSession();
        String connectionId = notificationService.handleConnection(userId, session);

        notificationService.handleSubscribe(connectionId, rideId);
        notificationService.handleUnsubscribe(connectionId, rideId);

        NotificationEvent event = NotificationEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .eventType("driver.location_updated")
                .rideId(rideId)
                .timestamp(Instant.now())
                .build();

        int deliveryCount = notificationService.processKafkaEvent(event);

        assertEquals(0, deliveryCount);
    }

    @Test
    @DisplayName("should_persist_and_recover_connection_state")
    void should_persist_and_recover_connection_state() throws IOException {
        String userId = "test-user-recovery";
        String rideId1 = "ride-1";
        String rideId2 = "ride-2";

        MockWebSocketSession session = new MockWebSocketSession();
        String connectionId = notificationService.handleConnection(userId, session);

        notificationService.handleSubscribe(connectionId, rideId1);
        notificationService.handleSubscribe(connectionId, rideId2);

        // Simulate recovery
        notificationService.recoverConnection(connectionId, userId);

        // Verify metrics
        NotificationService.NotificationMetrics metrics = notificationService.getMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getActiveConnections() > 0);
    }

    @Test
    @DisplayName("should_detect_and_skip_duplicate_messages")
    void should_detect_and_skip_duplicate_messages() throws IOException {
        String userId = "test-user-" + UUID.randomUUID();
        String rideId = "test-ride-" + UUID.randomUUID();
        String messageId = "duplicate-msg-" + UUID.randomUUID();

        MockWebSocketSession session = new MockWebSocketSession();
        String connectionId = notificationService.handleConnection(userId, session);
        notificationService.handleSubscribe(connectionId, rideId);

        NotificationEvent event = NotificationEvent.builder()
                .messageId(messageId)
                .eventType("driver.location_updated")
                .rideId(rideId)
                .timestamp(Instant.now())
                .build();

        // First delivery
        int count1 = notificationService.processKafkaEvent(event);
        int sentMessages1 = session.getSentMessages().size();

        // Second delivery (duplicate)
        int count2 = notificationService.processKafkaEvent(event);
        int sentMessages2 = session.getSentMessages().size();

        assertTrue(count1 > 0);
        assertEquals(0, count2); // Duplicate should not be delivered
        assertEquals(sentMessages1, sentMessages2); // No new messages sent
    }

    @Test
    @DisplayName("should_handle_heartbeat_keep_connection_alive")
    void should_handle_heartbeat_keep_connection_alive() throws IOException {
        String userId = "test-user-" + UUID.randomUUID();
        String rideId = "test-ride-" + UUID.randomUUID();

        MockWebSocketSession session = new MockWebSocketSession();
        String connectionId = notificationService.handleConnection(userId, session);
        notificationService.handleSubscribe(connectionId, rideId);

        // Send heartbeat
        notificationService.handleHeartbeat(connectionId);

        // Connection should still be active
        assertTrue(notificationService.getMetrics().getActiveConnections() > 0);
    }

    @Test
    @DisplayName("should_cleanup_on_disconnection")
    void should_cleanup_on_disconnection() throws IOException {
        String userId = "test-user-" + UUID.randomUUID();
        String rideId = "test-ride-" + UUID.randomUUID();

        MockWebSocketSession session = new MockWebSocketSession();
        String connectionId = notificationService.handleConnection(userId, session);
        notificationService.handleSubscribe(connectionId, rideId);

        long connectionsBeforeDisconnect = notificationService.getMetrics().getActiveConnections();

        notificationService.handleDisconnection(connectionId);

        long connectionsAfterDisconnect = notificationService.getMetrics().getActiveConnections();
        assertTrue(connectionsAfterDisconnect < connectionsBeforeDisconnect);
    }

    /**
     * Mock WebSocketSession for testing.
     */
    private static class MockWebSocketSession implements WebSocketSession {

        private final String id = UUID.randomUUID().toString();
        private final java.util.List<String> sentMessages = new java.util.ArrayList<>();
        private boolean open = true;

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return null;
        }

        @Override
        public org.springframework.http.HttpHeaders getHandshakeHeaders() {
            return org.springframework.http.HttpHeaders.EMPTY;
        }

        @Override
        public java.util.Map<String, Object> getAttributes() {
            return new java.util.concurrent.ConcurrentHashMap<>();
        }

        @Override
        public java.security.Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 64 * 1024;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 64 * 1024;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return Collections.emptyList();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
            if (message instanceof TextMessage) {
                sentMessages.add(((TextMessage) message).getPayload());
            }
        }

        @Override
        public void close() throws IOException {
            open = false;
        }

        @Override
        public void close(org.springframework.web.socket.CloseStatus status) throws IOException {
            open = false;
        }

        public java.util.List<String> getSentMessages() {
            return sentMessages;
        }
    }
}
