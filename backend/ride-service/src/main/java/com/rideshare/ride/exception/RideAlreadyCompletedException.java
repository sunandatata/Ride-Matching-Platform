package com.rideshare.ride.exception;

/**
 * Exception thrown when attempting to modify a completed or cancelled ride.
 */
public class RideAlreadyCompletedException extends RuntimeException {

    public RideAlreadyCompletedException(String rideId) {
        super("Ride " + rideId + " is already in a terminal state and cannot be modified");
    }

    public RideAlreadyCompletedException(String message, Throwable cause) {
        super(message, cause);
    }
}
