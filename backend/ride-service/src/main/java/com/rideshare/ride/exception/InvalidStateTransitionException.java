package com.rideshare.ride.exception;

import com.rideshare.ride.entity.RideStatus;

/**
 * Exception thrown when an invalid ride state transition is attempted.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(RideStatus from, RideStatus to) {
        super("Invalid state transition from " + from + " to " + to);
    }

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
