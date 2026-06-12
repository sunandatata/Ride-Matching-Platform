package com.rideshare.auth.exception;

/**
 * Base exception for authentication-related errors.
 */
public class AuthException extends RuntimeException {

    private final String errorCode;

    public AuthException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public AuthException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
