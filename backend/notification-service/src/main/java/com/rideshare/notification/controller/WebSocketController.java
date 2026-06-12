package com.rideshare.notification.controller;

import com.rideshare.notification.dto.WebSocketMessage;
import com.rideshare.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;

/**
 * WebSocket handler for real-time ride notifications.
 * Implements frame handling, message parsing, and subscription management.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketController extends TextWebSocketHandler {

    private static final String USER_ID_ATTR = "userId";
    private static final String CONNECTION_ID_ATTR = "connectionId";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Handle new WebSocket connection.
     * Extract user ID from principal or session attributes.
     *
     * @param session the WebSocket session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        String connectionId = notificationService.handleConnection(userId, session);

        session.getAttributes().put(USER_ID_ATTR, userId);
        session.getAttributes().put(CONNECTION_ID_ATTR, connectionId);

        log.info("WebSocket connection established - userId: {}, connectionId: {}", userId, connectionId);
    }

    /**
     * Handle incoming WebSocket messages.
     * Supports subscribe, unsubscribe, and ping message types.
     *
     * @param session the WebSocket session
     * @param message the incoming text message
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String connectionId = (String) session.getAttributes().get(CONNECTION_ID_ATTR);
        String userId = (String) session.getAttributes().get(USER_ID_ATTR);

        try {
            WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

            switch (wsMessage.getType()) {
                case "subscribe":
                    handleSubscribe(connectionId, userId, wsMessage.getRideId());
                    break;

                case "unsubscribe":
                    handleUnsubscribe(connectionId, userId, wsMessage.getRideId());
                    break;

                case "ping":
                    handlePing(connectionId);
                    break;

                default:
                    log.warn("Unknown message type: {}", wsMessage.getType());
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message - userId: {}, connectionId: {}",
                    userId, connectionId, e);
            sendError(session, "Invalid message format");
        }
    }

    /**
     * Handle WebSocket connection closure.
     * Clean up subscriptions and connection state.
     *
     * @param session the WebSocket session
     * @param closeStatus the close status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus closeStatus) throws Exception {
        String connectionId = (String) session.getAttributes().get(CONNECTION_ID_ATTR);
        if (connectionId != null) {
            notificationService.handleDisconnection(connectionId);
            log.info("WebSocket connection closed - connectionId: {}, status: {}", connectionId, closeStatus);
        }
    }

    /**
     * Handle transport errors.
     *
     * @param session the WebSocket session
     * @param exception the exception that occurred
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String connectionId = (String) session.getAttributes().get(CONNECTION_ID_ATTR);
        log.error("WebSocket transport error - connectionId: {}", connectionId, exception);
    }

    /**
     * Handle subscription to a ride's updates.
     *
     * @param connectionId the connection ID
     * @param userId the user ID
     * @param rideId the ride ID to subscribe to
     */
    private void handleSubscribe(String connectionId, String userId, String rideId) {
        if (rideId == null || rideId.isBlank()) {
            log.warn("Subscribe request missing rideId - userId: {}", userId);
            return;
        }

        notificationService.handleSubscribe(connectionId, rideId);
        log.debug("User subscribed to ride - userId: {}, rideId: {}", userId, rideId);
    }

    /**
     * Handle unsubscription from a ride's updates.
     *
     * @param connectionId the connection ID
     * @param userId the user ID
     * @param rideId the ride ID to unsubscribe from
     */
    private void handleUnsubscribe(String connectionId, String userId, String rideId) {
        if (rideId == null || rideId.isBlank()) {
            log.warn("Unsubscribe request missing rideId - userId: {}", userId);
            return;
        }

        notificationService.handleUnsubscribe(connectionId, rideId);
        log.debug("User unsubscribed from ride - userId: {}, rideId: {}", userId, rideId);
    }

    /**
     * Handle ping/heartbeat message to keep connection alive.
     *
     * @param connectionId the connection ID
     */
    private void handlePing(String connectionId) {
        notificationService.handleHeartbeat(connectionId);
    }

    /**
     * Extract user ID from WebSocket session.
     * Can be from principal name, custom attribute, or JWT token.
     *
     * @param session the WebSocket session
     * @return user ID
     */
    private String extractUserId(WebSocketSession session) {
        // Attempt to get from principal
        if (session.getPrincipal() != null && session.getPrincipal().getName() != null) {
            return session.getPrincipal().getName();
        }

        // Attempt to get from attributes (set by custom interceptor)
        Object userIdAttr = session.getAttributes().get(USER_ID_ATTR);
        if (userIdAttr != null) {
            return userIdAttr.toString();
        }

        // Fallback to session ID
        return "anonymous-" + session.getId();
    }

    /**
     * Send error message to client.
     *
     * @param session the WebSocket session
     * @param errorMessage the error message
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            WebSocketMessage errorMsg = WebSocketMessage.builder()
                    .type("error")
                    .data(errorMessage)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
        } catch (Exception e) {
            log.warn("Failed to send error message - sessionId: {}", session.getId(), e);
        }
    }
}
