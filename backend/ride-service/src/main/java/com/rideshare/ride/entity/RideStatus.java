package com.rideshare.ride.entity;

/**
 * Enum representing the ride lifecycle states.
 * Defines valid state transitions in the ride state machine.
 */
public enum RideStatus {
    REQUESTED,      // Initial state: ride request created
    MATCHED,        // Matching engine assigned driver (30 sec expiry)
    ACCEPTED,       // Driver confirmed or auto-accepted
    ARRIVED,        // Driver at pickup location
    STARTED,        // Rider boarded, journey started
    COMPLETED,      // Rider arrived at dropoff
    CANCELLED;      // Cancelled at any point

    /**
     * Validates if a state transition is legal.
     *
     * @param from source state
     * @param to target state
     * @return true if transition is valid, false otherwise
     */
    public static boolean isValidTransition(RideStatus from, RideStatus to) {
        return switch (from) {
            case REQUESTED -> to == MATCHED || to == CANCELLED;
            case MATCHED -> to == ACCEPTED || to == CANCELLED;
            case ACCEPTED -> to == ARRIVED || to == CANCELLED;
            case ARRIVED -> to == STARTED || to == CANCELLED;
            case STARTED -> to == COMPLETED || to == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * Checks if the ride is in a terminal state (no further transitions allowed).
     *
     * @return true if ride is in terminal state
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * Checks if the ride is in an active state (ride is ongoing).
     *
     * @return true if ride is active
     */
    public boolean isActive() {
        return this == STARTED;
    }
}
