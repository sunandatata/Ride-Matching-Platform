package com.rideshare.notification.service;

import com.rideshare.notification.util.MetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketConnectionManager.
 * Tests connection lifecycle and session management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketConnectionManager")
class WebSocketConnectionManagerTest {

    private WebSocketConnectionManager connectionManager;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @Mock
    private MetricsRecorder metricsRecorder;

    @BeforeEach
    void setUp() {
        connectionManager = new WebSocketConnectionManager(metricsRecorder);
    }

    @Test
    @DisplayName("should_register_connection")
    void should_register_connection() {
        String userId = "user-123";
        String connectionId = "conn-456";

        connectionManager.registerConnection(userId, connectionId, session1);

        assertTrue(connectionManager.isConnectionActive(connectionId));
        assertEquals(1, connectionManager.getTotalConnections());
        verify(metricsRecorder).recordConnectionOpened();
    }

    @Test
    @DisplayName("should_unregister_connection")
    void should_unregister_connection() {
        String userId = "user-123";
        String connectionId = "conn-456";

        connectionManager.registerConnection(userId, connectionId, session1);
        assertEquals(1, connectionManager.getTotalConnections());

        connectionManager.unregisterConnection(connectionId);

        assertEquals(0, connectionManager.getTotalConnections());
        verify(metricsRecorder).recordConnectionClosed();
    }

    @Test
    @DisplayName("should_get_connection_by_id")
    void should_get_connection_by_id() {
        String userId = "user-123";
        String connectionId = "conn-456";

        connectionManager.registerConnection(userId, connectionId, session1);

        Optional<WebSocketSession> connection = connectionManager.getConnection(connectionId);

        assertTrue(connection.isPresent());
        assertEquals(session1, connection.get());
    }

    @Test
    @DisplayName("should_return_empty_for_nonexistent_connection")
    void should_return_empty_for_nonexistent_connection() {
        Optional<WebSocketSession> connection = connectionManager.getConnection("conn-nonexistent");

        assertTrue(connection.isEmpty());
    }

    @Test
    @DisplayName("should_get_all_sessions_for_user")
    void should_get_all_sessions_for_user() {
        String userId = "user-123";

        connectionManager.registerConnection(userId, "conn-1", session1);
        connectionManager.registerConnection(userId, "conn-2", session2);

        Collection<WebSocketSession> userSessions = connectionManager.getUserSessions(userId);

        assertEquals(2, userSessions.size());
        assertTrue(userSessions.contains(session1));
        assertTrue(userSessions.contains(session2));
    }

    @Test
    @DisplayName("should_get_user_id_for_connection")
    void should_get_user_id_for_connection() {
        String userId = "user-123";
        String connectionId = "conn-456";

        connectionManager.registerConnection(userId, connectionId, session1);

        Optional<String> retrievedUserId = connectionManager.getUserId(connectionId);

        assertTrue(retrievedUserId.isPresent());
        assertEquals(userId, retrievedUserId.get());
    }

    @Test
    @DisplayName("should_check_connection_active_status")
    void should_check_connection_active_status() {
        String userId = "user-123";
        String connectionId = "conn-456";

        when(session1.isOpen()).thenReturn(true);

        connectionManager.registerConnection(userId, connectionId, session1);

        assertTrue(connectionManager.isConnectionActive(connectionId));
    }

    @Test
    @DisplayName("should_get_user_connection_count")
    void should_get_user_connection_count() {
        String userId = "user-123";

        connectionManager.registerConnection(userId, "conn-1", session1);
        connectionManager.registerConnection(userId, "conn-2", session2);

        long count = connectionManager.getUserConnectionCount(userId);

        assertEquals(2, count);
    }

    @Test
    @DisplayName("should_handle_multiple_users_independently")
    void should_handle_multiple_users_independently() {
        String user1 = "user-1";
        String user2 = "user-2";

        connectionManager.registerConnection(user1, "conn-1", session1);
        connectionManager.registerConnection(user2, "conn-2", session2);

        assertEquals(1, connectionManager.getUserConnectionCount(user1));
        assertEquals(1, connectionManager.getUserConnectionCount(user2));
        assertEquals(2, connectionManager.getTotalConnections());

        connectionManager.unregisterConnection("conn-1");

        assertEquals(0, connectionManager.getUserConnectionCount(user1));
        assertEquals(1, connectionManager.getUserConnectionCount(user2));
        assertEquals(1, connectionManager.getTotalConnections());
    }
}
