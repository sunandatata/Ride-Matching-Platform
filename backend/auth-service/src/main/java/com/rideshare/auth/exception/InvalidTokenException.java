package com.rideshare.auth.exception;

/**
 * Thrown when JWT token is invalid or expired.
 */
public class InvalidTokenException extends AuthException {

    public InvalidTokenException(String message) {
        super(message, "INVALID_TOKEN");
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, "INVALID_TOKEN", cause);
    }
}
