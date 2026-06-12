package com.rideshare.ride.validator;

import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.entity.RideStatus;
import com.rideshare.ride.exception.InvalidStateTransitionException;
import com.rideshare.ride.exception.RideAlreadyCompletedException;
import org.springframework.stereotype.Component;

/**
 * Validates ride state transitions and ride state prerequisites.
 * Enforces the state machine rules at the business logic level.
 */
@Component
public class RideStateValidator {

    /**
     * Validates that a state transition is legal.
     *
     * @param ride the ride entity
     * @param targetStatus the desired target status
     * @throws InvalidStateTransitionException if the transition is invalid
     * @throws RideAlreadyCompletedException if the ride is in a terminal state
     */
    public void validateStateTransition(Ride ride, RideStatus targetStatus) {
        RideStatus currentStatus = ride.getStatus();

        // Check if ride is in a terminal state
        if (currentStatus.isTerminal()) {
            throw new RideAlreadyCompletedException(
                "Ride " + ride.getId() + " is in terminal state: " + currentStatus
            );
        }

        // Check if transition is valid according to state machine
        if (!RideStatus.isValidTransition(currentStatus, targetStatus)) {
            throw new InvalidStateTransitionException(currentStatus, targetStatus);
        }
    }

    /**
     * Validates prerequisites for transitioning to a specific status.
     * Additional validation beyond the state machine rules.
     *
     * @param ride the ride entity
     * @param targetStatus the target status
     * @throws IllegalStateException if prerequisites are not met
     */
    public void validateTransitionPrerequisites(Ride ride, RideStatus targetStatus) {
        switch (targetStatus) {
            case MATCHED:
                // MATCHED requires rider_id and location information
                if (ride.getRiderId() == null) {
                    throw new IllegalStateException("Cannot match ride: rider_id is null");
                }
                break;

            case ACCEPTED:
                // ACCEPTED requires driver to be assigned
                if (ride.getDriverId() == null) {
                    throw new IllegalStateException("Cannot accept ride: driver_id is null");
                }
                break;

            case ARRIVED:
                // ARRIVED requires driver confirmation
                if (ride.getDriverId() == null) {
                    throw new IllegalStateException("Cannot arrive: driver_id is null");
                }
                break;

            case STARTED:
                // STARTED requires rider to be ready
                if (ride.getDriverId() == null) {
                    throw new IllegalStateException("Cannot start ride: driver_id is null");
                }
                break;

            case COMPLETED:
                // COMPLETED requires ride to have started
                if (ride.getStatus() != RideStatus.STARTED) {
                    throw new IllegalStateException("Ride must be in STARTED state to complete");
                }
                break;

            case CANCELLED:
                // Cancellation is always allowed from non-terminal states
                break;

            case REQUESTED:
                // REQUESTED is initial state only
                throw new IllegalStateException("Cannot transition to REQUESTED state");
        }
    }

    /**
     * Validates that a ride can accept a driver assignment.
     * Called when Matching Engine assigns a driver.
     *
     * @param ride the ride entity
     * @throws IllegalStateException if the ride cannot accept a driver assignment
     */
    public void validateCanAssignDriver(Ride ride) {
        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new IllegalStateException(
                "Cannot assign driver to ride in " + ride.getStatus() + " state. " +
                "Ride must be in REQUESTED state."
            );
        }
    }

    /**
     * Validates that a ride can be cancelled.
     * Some restrictions may apply based on ride state.
     *
     * @param ride the ride entity
     * @throws RideAlreadyCompletedException if the ride is in a terminal state
     */
    public void validateCanCancel(Ride ride) {
        if (ride.getStatus().isTerminal()) {
            throw new RideAlreadyCompletedException(
                "Cannot cancel ride " + ride.getId() + ": ride is already in terminal state " + ride.getStatus()
            );
        }
    }

    /**
     * Validates that a ride can be rated.
     * Can only rate completed rides.
     *
     * @param ride the ride entity
     * @throws IllegalStateException if the ride cannot be rated
     */
    public void validateCanRate(Ride ride) {
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new IllegalStateException(
                "Cannot rate ride " + ride.getId() + ": ride must be completed. Current status: " + ride.getStatus()
            );
        }
    }
}
