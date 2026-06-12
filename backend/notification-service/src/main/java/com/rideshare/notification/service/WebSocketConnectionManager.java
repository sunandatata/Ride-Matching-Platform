package com.rideshare.notification.service;

import com.rideshare.notification.exception.WebSocketConnectionException;
import com.rideshare.notification.util.MetricsRecorder;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of WebSocket connections.
 * Tracks active connections by user and connection ID for message routing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketConnectionManager {

    // Map: userId -> Map<connectionId, WebSocketSession>
    private final Map<String, Map<String, WebSocketSession>> userConnections = new ConcurrentHashMap<>();

    // Map: connectionId -> userId (for quick reverse lookup)
    private final Map<String, String> connectionToUserId = new ConcurrentHashMap<>();

    private final MetricsRecorder metricsRecorder;

    /**
     * Register a new WebSocket connection.
     *
     * @param userId the authenticated user ID
     * @param connectionId the connection session ID
     * @param session the WebSocketSession
     */
    public void registerConnection(String userId, String connectionId, WebSocketSession session) {
        userConnections.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(connectionId, session);
        connectionToUserId.put(connectionId, userId);
        metricsRecorder.recordConnectionOpened();
        metricsRecorder.recordActiveConnections(getTotalConnections());
        log.info("Registered connection - user: {}, connection: {}, total: {}",
                userId, connectionId, getTotalConnections());
    }

    /**
     * Unregister a WebSocket connection.
     *
     * @param connectionId the connection session ID
     */
    public void unregisterConnection(String connectionId) {
        String userId = connectionToUserId.remove(connectionId);
        if (userId != null) {
            Map<String, WebSocketSession> userSessions = userConnections.get(userId);
            if (userSessions != null) {
                userSessions.remove(connectionId);
                if (userSessions.isEmpty()) {
                    userConnections.remove(userId);
                }
            }
            metricsRecorder.recordConnectionClosed();
            metricsRecorder.recordActiveConnections(getTotalConnections());
            log.info("Unregistered connection - user: {}, connection: {}, total: {}",
                    userId, connectionId, getTotalConnections());
        }
    }

    /**
     * Get a specific WebSocket session by connection ID.
     *
     * @param connectionId the connection session ID
     * @return Optional containing the WebSocketSession
     */
    public Optional<WebSocketSession> getConnection(String connectionId) {
        String userId = connectionToUserId.get(connectionId);
        if (userId == null) {
            return Optional.empty();
        }
        Map<String, WebSocketSession> userSessions = userConnections.get(userId);
        if (userSessions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userSessions.get(connectionId));
    }

    /**
     * Get all active sessions for a user.
     *
     * @param userId the user ID
     * @return Collection of WebSocketSessions for this user
     */
    public Collection<WebSocketSession> getUserSessions(String userId) {
        Map<String, WebSocketSession> userSessions = userConnections.get(userId);
        if (userSessions == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(userSessions.values());
    }

    /**
     * Get the user ID for a connection.
     *
     * @param connectionId the connection session ID
     * @return Optional containing the user ID
     */
    public Optional<String> getUserId(String connectionId) {
        return Optional.ofNullable(connectionToUserId.get(connectionId));
    }

    /**
     * Check if a connection is tracked as active.
     *
     * @param connectionId the connection session ID
     * @return true if connection is registered
     */
    public boolean isConnectionActive(String connectionId) {
        return getConnection(connectionId)
                .map(session -> session.isOpen() || connectionToUserId.containsKey(connectionId))
                .orElse(false);
    }

    /**
     * Get total number of active connections across all users.
     *
     * @return total connection count
     */
    public long getTotalConnections() {
        return connectionToUserId.size();
    }

    /**
     * Get number of connections for a specific user.
     *
     * @param userId the user ID
     * @return connection count for user
     */
    public long getUserConnectionCount(String userId) {
        Map<String, WebSocketSession> userSessions = userConnections.get(userId);
        return userSessions != null ? userSessions.size() : 0;
    }

    /**
     * Broadcast message to all sessions for a user.
     * Used for closing all connections during logout or account changes.
     *
     * @param userId the user ID
     * @param message the message to broadcast
     */
    public void broadcastToUser(String userId, String message) {
        Collection<WebSocketSession> sessions = getUserSessions(userId);
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                } catch (Exception e) {
                    log.warn("Failed to send message to session: {}", session.getId(), e);
                }
            }
        });
    }

    /**
     * Close all active connections gracefully.
     * Used during shutdown to drain connections.
     */
    public void closeAllConnections() {
        log.info("Closing all {} active connections", getTotalConnections());
        List<String> connectionIds = new ArrayList<>(connectionToUserId.keySet());
        connectionIds.forEach(this::closeConnection);
    }

    /**
     * Close a specific connection gracefully.
     *
     * @param connectionId the connection session ID
     */
    private void closeConnection(String connectionId) {
        getConnection(connectionId).ifPresent(session -> {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Error closing WebSocket session: {}", connectionId, e);
            }
        });
        unregisterConnection(connectionId);
    }
}
