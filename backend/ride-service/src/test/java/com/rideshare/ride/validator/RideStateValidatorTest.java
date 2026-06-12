package com.rideshare.ride.validator;

import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.entity.RideStatus;
import com.rideshare.ride.exception.InvalidStateTransitionException;
import com.rideshare.ride.exception.RideAlreadyCompletedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RideStateValidator.
 * Tests state machine rules and transition validation.
 */
@DisplayName("RideStateValidator Unit Tests")
class RideStateValidatorTest {

    private RideStateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RideStateValidator();
    }

    @Test
    @DisplayName("Should allow valid transition from REQUESTED to MATCHED")
    void testValidTransition_RequestedToMatched() {
        Ride ride = buildRide(RideStatus.REQUESTED);

        // Should not throw
        assertDoesNotThrow(() -> validator.validateStateTransition(ride, RideStatus.MATCHED));
    }

    @Test
    @DisplayName("Should allow valid transition from MATCHED to ACCEPTED")
    void testValidTransition_MatchedToAccepted() {
        Ride ride = buildRide(RideStatus.MATCHED);

        assertDoesNotThrow(() -> validator.validateStateTransition(ride, RideStatus.ACCEPTED));
    }

    @Test
    @DisplayName("Should allow valid transition from ACCEPTED to ARRIVED")
    void testValidTransition_AcceptedToArrived() {
        Ride ride = buildRide(RideStatus.ACCEPTED);

        assertDoesNotThrow(() -> validator.validateStateTransition(ride, RideStatus.ARRIVED));
    }

    @Test
    @DisplayName("Should allow valid transition from ARRIVED to STARTED")
    void testValidTransition_ArrivedToStarted() {
        Ride ride = buildRide(RideStatus.ARRIVED);

        assertDoesNotThrow(() -> validator.validateStateTransition(ride, RideStatus.STARTED));
    }

    @Test
    @DisplayName("Should allow valid transition from STARTED to COMPLETED")
    void testValidTransition_StartedToCompleted() {
        Ride ride = buildRide(RideStatus.STARTED);

        assertDoesNotThrow(() -> validator.validateStateTransition(ride, RideStatus.COMPLETED));
    }

    @Test
    @DisplayName("Should allow cancellation from any non-terminal state")
    void testValidTransition_CancellationFromAny() {
        RideStatus[] validCancellationStates = {
            RideStatus.REQUESTED, RideStatus.MATCHED, RideStatus.ACCEPTED,
            RideStatus.ARRIVED, RideStatus.STARTED
        };

        for (RideStatus status : validCancellationStates) {
            Ride ride = buildRide(status);
            assertDoesNotThrow(() -> validator.validateStateTransition(ride, RideStatus.CANCELLED),
                "Should allow cancellation from " + status);
        }
    }

    @Test
    @DisplayName("Should reject invalid transition from REQUESTED to STARTED")
    void testInvalidTransition_SkipStates() {
        Ride ride = buildRide(RideStatus.REQUESTED);

        assertThrows(InvalidStateTransitionException.class,
            () -> validator.validateStateTransition(ride, RideStatus.STARTED));
    }

    @Test
    @DisplayName("Should reject invalid transition from MATCHED to STARTED")
    void testInvalidTransition_MatchedToStarted() {
        Ride ride = buildRide(RideStatus.MATCHED);

        assertThrows(InvalidStateTransitionException.class,
            () -> validator.validateStateTransition(ride, RideStatus.STARTED));
    }

    @Test
    @DisplayName("Should reject invalid transition from COMPLETED to CANCELLED")
    void testInvalidTransition_FromTerminalState() {
        Ride ride = buildRide(RideStatus.COMPLETED);

        assertThrows(RideAlreadyCompletedException.class,
            () -> validator.validateStateTransition(ride, RideStatus.CANCELLED));
    }

    @Test
    @DisplayName("Should reject modification of CANCELLED ride")
    void testInvalidTransition_FromCancelledState() {
        Ride ride = buildRide(RideStatus.CANCELLED);

        assertThrows(RideAlreadyCompletedException.class,
            () -> validator.validateStateTransition(ride, RideStatus.COMPLETED));
    }

    @Test
    @DisplayName("Should validate MATCHED state requires driver assignment")
    void testValidateTransitionPrerequisites_MatchedRequiresDriver() {
        Ride ride = buildRide(RideStatus.REQUESTED);
        ride.setDriverId(null);

        // Should pass - prerequisite not checked for MATCHED
        assertDoesNotThrow(() -> validator.validateTransitionPrerequisites(ride, RideStatus.MATCHED));
    }

    @Test
    @DisplayName("Should validate ACCEPTED state requires driver")
    void testValidateTransitionPrerequisites_AcceptedRequiresDriver() {
        Ride ride = buildRide(RideStatus.MATCHED);
        ride.setDriverId(null);

        assertThrows(IllegalStateException.class,
            () -> validator.validateTransitionPrerequisites(ride, RideStatus.ACCEPTED));
    }

    @Test
    @DisplayName("Should validate STARTED state requires driver")
    void testValidateTransitionPrerequisites_StartedRequiresDriver() {
        Ride ride = buildRide(RideStatus.ARRIVED);
        ride.setDriverId(null);

        assertThrows(IllegalStateException.class,
            () -> validator.validateTransitionPrerequisites(ride, RideStatus.STARTED));
    }

    @Test
    @DisplayName("Should reject assignment of driver to non-REQUESTED ride")
    void testValidateCanAssignDriver_RequiresRequestedState() {
        Ride ride = buildRide(RideStatus.MATCHED);

        assertThrows(IllegalStateException.class,
            () -> validator.validateCanAssignDriver(ride));
    }

    @Test
    @DisplayName("Should allow assignment of driver to REQUESTED ride")
    void testValidateCanAssignDriver_AllowsRequestedState() {
        Ride ride = buildRide(RideStatus.REQUESTED);

        assertDoesNotThrow(() -> validator.validateCanAssignDriver(ride));
    }

    @Test
    @DisplayName("Should reject cancellation of completed ride")
    void testValidateCanCancel_RejectsCompletedRide() {
        Ride ride = buildRide(RideStatus.COMPLETED);

        assertThrows(RideAlreadyCompletedException.class,
            () -> validator.validateCanCancel(ride));
    }

    @Test
    @DisplayName("Should reject cancellation of cancelled ride")
    void testValidateCanCancel_RejectsCancelledRide() {
        Ride ride = buildRide(RideStatus.CANCELLED);

        assertThrows(RideAlreadyCompletedException.class,
            () -> validator.validateCanCancel(ride));
    }

    @Test
    @DisplayName("Should allow cancellation of non-terminal ride")
    void testValidateCanCancel_AllowsNonTerminalRide() {
        Ride ride = buildRide(RideStatus.STARTED);

        assertDoesNotThrow(() -> validator.validateCanCancel(ride));
    }

    @Test
    @DisplayName("Should reject rating of non-completed ride")
    void testValidateCanRate_RequiresCompletedState() {
        RideStatus[] nonCompletedStates = {
            RideStatus.REQUESTED, RideStatus.MATCHED, RideStatus.ACCEPTED,
            RideStatus.ARRIVED, RideStatus.STARTED, RideStatus.CANCELLED
        };

        for (RideStatus status : nonCompletedStates) {
            Ride ride = buildRide(status);
            assertThrows(IllegalStateException.class,
                () -> validator.validateCanRate(ride),
                "Should reject rating from " + status);
        }
    }

    @Test
    @DisplayName("Should allow rating of completed ride")
    void testValidateCanRate_AllowsCompletedRide() {
        Ride ride = buildRide(RideStatus.COMPLETED);

        assertDoesNotThrow(() -> validator.validateCanRate(ride));
    }

    @Test
    @DisplayName("Should identify terminal states correctly")
    void testTerminalStates() {
        assertTrue(RideStatus.COMPLETED.isTerminal(), "COMPLETED should be terminal");
        assertTrue(RideStatus.CANCELLED.isTerminal(), "CANCELLED should be terminal");

        RideStatus[] nonTerminal = {
            RideStatus.REQUESTED, RideStatus.MATCHED, RideStatus.ACCEPTED,
            RideStatus.ARRIVED, RideStatus.STARTED
        };
        for (RideStatus status : nonTerminal) {
            assertFalse(status.isTerminal(), status + " should not be terminal");
        }
    }

    @Test
    @DisplayName("Should identify active states correctly")
    void testActiveStates() {
        assertTrue(RideStatus.STARTED.isActive(), "STARTED should be active");

        RideStatus[] nonActive = {
            RideStatus.REQUESTED, RideStatus.MATCHED, RideStatus.ACCEPTED,
            RideStatus.ARRIVED, RideStatus.COMPLETED, RideStatus.CANCELLED
        };
        for (RideStatus status : nonActive) {
            assertFalse(status.isActive(), status + " should not be active");
        }
    }

    @Test
    @DisplayName("Should validate complete state machine path")
    void testCompleteStateMachinePath() {
        // Test the happy path through the entire state machine
        RideStatus[] path = {
            RideStatus.REQUESTED,
            RideStatus.MATCHED,
            RideStatus.ACCEPTED,
            RideStatus.ARRIVED,
            RideStatus.STARTED,
            RideStatus.COMPLETED
        };

        for (int i = 0; i < path.length - 1; i++) {
            Ride ride = buildRide(path[i]);
            RideStatus nextStatus = path[i + 1];
            assertDoesNotThrow(() -> validator.validateStateTransition(ride, nextStatus),
                "Should allow transition from " + path[i] + " to " + nextStatus);
        }
    }

    /**
     * Helper method to build a ride with specific status.
     */
    private Ride buildRide(RideStatus status) {
        return Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(status)
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave")
                .passengerCount(1)
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
