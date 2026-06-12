package com.rideshare.ride.entity;

/**
 * Enum for different ride event types.
 * Used in event sourcing to track all ride state changes and actions.
 */
public enum RideEventType {
    RIDE_REQUESTED,
    RIDE_MATCHED,
    RIDE_ACCEPTED,
    RIDE_ARRIVED,
    RIDE_STARTED,
    RIDE_COMPLETED,
    RIDE_CANCELLED,
    DRIVER_ASSIGNED,
    RATING_PROVIDED,
    PAYMENT_PROCESSED
}
