package com.rideshare.location.exception;

/**
 * Base exception for Location Service errors.
 */
public class LocationServiceException extends RuntimeException {

    private final String errorCode;

    public LocationServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public LocationServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
