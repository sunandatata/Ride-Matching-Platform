package com.rideshare.notification.exception;

/**
 * Exception thrown when WebSocket connection operations fail.
 */
public class WebSocketConnectionException extends NotificationException {

    public WebSocketConnectionException(String message) {
        super(message, "WEBSOCKET_CONNECTION_ERROR");
    }

    public WebSocketConnectionException(String message, Throwable cause) {
        super(message, "WEBSOCKET_CONNECTION_ERROR", cause);
    }
}
