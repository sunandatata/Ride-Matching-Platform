package com.rideshare.ride.service;

import com.rideshare.ride.dto.*;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.entity.RideEvent;
import com.rideshare.ride.entity.RideEventType;
import com.rideshare.ride.entity.RideStatus;
import com.rideshare.ride.event.RideEventPublisher;
import com.rideshare.ride.exception.InvalidStateTransitionException;
import com.rideshare.ride.exception.RideNotFoundException;
import com.rideshare.ride.repository.RideEventRepository;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.ride.validator.RideStateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RideService - critical state machine logic.
 * Tests all state transitions, validations, and event publishing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RideService Unit Tests")
class RideServiceTest {

    @Mock
    private RideRepository rideRepository;

    @Mock
    private RideEventRepository eventRepository;

    @Mock
    private RideStateValidator stateValidator;

    @Mock
    private RideEventPublisher eventPublisher;

    @Mock
    private PaymentProcessor paymentProcessor;

    private RideService rideService;

    @BeforeEach
    void setUp() {
        rideService = new RideService(
            rideRepository,
            eventRepository,
            stateValidator,
            eventPublisher,
            paymentProcessor
        );
    }

    @Test
    @DisplayName("Should create a new ride with REQUESTED status")
    void testCreateRide_Success() {
        // Arrange
        CreateRideRequest request = CreateRideRequest.builder()
                .riderId("RIDER-123")
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St, NYC")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave, NYC")
                .passengerCount(2)
                .build();

        Ride savedRide = Ride.builder()
                .id("RIDE-abc123")
                .riderId("RIDER-123")
                .status(RideStatus.REQUESTED)
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St, NYC")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave, NYC")
                .passengerCount(2)
                .shardId(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rideRepository.save(any(Ride.class))).thenReturn(savedRide);

        // Act
        RideResponse response = rideService.createRide(request);

        // Assert
        assertNotNull(response);
        assertEquals("RIDE-abc123", response.getId());
        assertEquals("RIDER-123", response.getRiderId());
        assertEquals(RideStatus.REQUESTED, response.getStatus());
        assertEquals(2, response.getPassengerCount());

        // Verify repository and event publisher called
        verify(rideRepository).save(any(Ride.class));
        verify(eventRepository).save(any(RideEvent.class));
        verify(eventPublisher).publishRideRequested(any(Ride.class));
    }

    @Test
    @DisplayName("Should retrieve a ride by ID")
    void testGetRide_Success() {
        // Arrange
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .status(RideStatus.REQUESTED)
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));

        // Act
        RideResponse response = rideService.getRide("RIDE-123");

        // Assert
        assertNotNull(response);
        assertEquals("RIDE-123", response.getId());
        assertEquals(RideStatus.REQUESTED, response.getStatus());
    }

    @Test
    @DisplayName("Should throw RideNotFoundException when ride does not exist")
    void testGetRide_NotFound() {
        // Arrange
        when(rideRepository.findById("INVALID")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RideNotFoundException.class, () -> rideService.getRide("INVALID"));
    }

    @Test
    @DisplayName("Should assign driver and transition to MATCHED")
    void testAssignDriver_Success() {
        // Arrange
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .status(RideStatus.REQUESTED)
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        AssignDriverRequest request = AssignDriverRequest.builder()
                .driverId("DRIVER-456")
                .estimatedFare(new BigDecimal("25.50"))
                .estimatedDurationSeconds(600)
                .driverETA(120)
                .build();

        Ride matchedRide = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.MATCHED)
                .estimatedFare(new BigDecimal("25.50"))
                .estimatedDurationSeconds(600)
                .driverETA(120)
                .matchedAt(LocalDateTime.now())
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(matchedRide);

        // Act
        RideResponse response = rideService.assignDriver("RIDE-123", request);

        // Assert
        assertEquals(RideStatus.MATCHED, response.getStatus());
        assertEquals("DRIVER-456", response.getDriverId());
        assertEquals(new BigDecimal("25.50"), response.getEstimatedFare());
        assertNotNull(response.getMatchedAt());

        // Verify Kafka event published
        verify(eventPublisher).publishRideMatched(any(Ride.class));
    }

    @Test
    @DisplayName("Should transition from MATCHED to ACCEPTED")
    void testUpdateRideStatus_RequestedToMatched() {
        // Arrange
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.MATCHED)
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .status(RideStatus.ACCEPTED)
                .initiatorId("DRIVER-456")
                .initiatorType("DRIVER")
                .build();

        Ride acceptedRide = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.ACCEPTED)
                .acceptedAt(LocalDateTime.now())
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(acceptedRide);

        // Act
        RideResponse response = rideService.updateRideStatus("RIDE-123", request);

        // Assert
        assertEquals(RideStatus.ACCEPTED, response.getStatus());
        assertNotNull(response.getAcceptedAt());
        verify(eventPublisher).publishRideAccepted(any(Ride.class));
    }

    @Test
    @DisplayName("Should transition from STARTED to COMPLETED and process payment")
    void testCompleteRide_Success() {
        // Arrange
        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(15);
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.STARTED)
                .startedAt(startedAt)
                .shardId(3)
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now())
                .build();

        BigDecimal actualFare = new BigDecimal("28.75");

        Ride completedRide = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.COMPLETED)
                .actualFare(actualFare)
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .actualDurationSeconds(900)
                .shardId(3)
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now())
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(completedRide);
        when(paymentProcessor.processPayment(any(Ride.class), eq(actualFare))).thenReturn(true);

        // Act
        RideResponse response = rideService.completeRide("RIDE-123", actualFare);

        // Assert
        assertEquals(RideStatus.COMPLETED, response.getStatus());
        assertEquals(actualFare, response.getActualFare());
        assertEquals(900, response.getActualDurationSeconds());
        assertNotNull(response.getCompletedAt());

        // Verify payment processing
        verify(paymentProcessor).processPayment(any(Ride.class), eq(actualFare));
        verify(eventPublisher).publishRideCompleted(any(Ride.class));
    }

    @Test
    @DisplayName("Should cancel ride at any non-terminal state")
    void testCancelRide_Success() {
        // Arrange
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.ACCEPTED)
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CancelRideRequest request = CancelRideRequest.builder()
                .reason("Driver not arriving")
                .initiatorId("RIDER-123")
                .initiatorType("RIDER")
                .build();

        Ride cancelledRide = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.CANCELLED)
                .cancellationReason("Driver not arriving")
                .cancellationInitiator("RIDER")
                .cancelledAt(LocalDateTime.now())
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(cancelledRide);

        // Act
        RideResponse response = rideService.cancelRide("RIDE-123", request);

        // Assert
        assertEquals(RideStatus.CANCELLED, response.getStatus());
        assertEquals("Driver not arriving", response.getCancellationReason());
        assertEquals("RIDER", response.getCancellationInitiator());
        assertNotNull(response.getCancelledAt());

        verify(eventPublisher).publishRideCancelled(any(Ride.class));
    }

    @Test
    @DisplayName("Should rate a completed ride")
    void testRateRide_Success() {
        // Arrange
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.COMPLETED)
                .completedAt(LocalDateTime.now())
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        RatingRequest request = RatingRequest.builder()
                .raterId("RIDER-123")
                .raterType("RIDER")
                .rating(5)
                .feedback("Excellent service!")
                .build();

        Ride ratedRide = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .driverId("DRIVER-456")
                .status(RideStatus.COMPLETED)
                .driverRating(5)
                .driverFeedback("Excellent service!")
                .completedAt(LocalDateTime.now())
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));
        when(rideRepository.save(any(Ride.class))).thenReturn(ratedRide);

        // Act
        RideResponse response = rideService.rateRide("RIDE-123", request);

        // Assert
        assertEquals(5, response.getDriverRating());
        assertEquals("Excellent service!", response.getDriverFeedback());
        verify(eventRepository).save(any(RideEvent.class));
    }

    @Test
    @DisplayName("Should throw exception for invalid state transition")
    void testUpdateRideStatus_InvalidTransition() {
        // Arrange
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .status(RideStatus.COMPLETED)
                .shardId(3)
                .build();

        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .status(RideStatus.STARTED)
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));
        doThrow(new InvalidStateTransitionException(RideStatus.COMPLETED, RideStatus.STARTED))
                .when(stateValidator).validateStateTransition(ride, RideStatus.STARTED);

        // Act & Assert
        assertThrows(InvalidStateTransitionException.class,
            () -> rideService.updateRideStatus("RIDE-123", request));
    }

    @Test
    @DisplayName("Should reject rating of non-completed ride")
    void testRateRide_NotCompleted() {
        // Arrange
        Ride ride = Ride.builder()
                .id("RIDE-123")
                .status(RideStatus.STARTED)
                .shardId(3)
                .build();

        RatingRequest request = RatingRequest.builder()
                .raterId("RIDER-123")
                .raterType("RIDER")
                .rating(5)
                .build();

        when(rideRepository.findById("RIDE-123")).thenReturn(Optional.of(ride));
        doThrow(new IllegalStateException("Cannot rate ride: ride must be completed"))
                .when(stateValidator).validateCanRate(ride);

        // Act & Assert
        assertThrows(IllegalStateException.class,
            () -> rideService.rateRide("RIDE-123", request));
    }

    @Test
    @DisplayName("Should transition through complete state machine")
    void testCompleteStateMachine() {
        // Arrange - Create a ride
        Ride requestedRide = Ride.builder()
                .id("RIDE-123")
                .riderId("RIDER-123")
                .status(RideStatus.REQUESTED)
                .shardId(3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Act - Test creation
        when(rideRepository.save(any(Ride.class))).thenReturn(requestedRide);
        CreateRideRequest createRequest = CreateRideRequest.builder()
                .riderId("RIDER-123")
                .pickupLatitude(40.7128)
                .pickupLongitude(-74.0060)
                .pickupAddress("123 Main St")
                .dropoffLatitude(40.7580)
                .dropoffLongitude(-73.9855)
                .dropoffAddress("456 Park Ave")
                .passengerCount(1)
                .build();
        rideService.createRide(createRequest);

        // Assert all state transitions are validated
        assertTrue(RideStatus.isValidTransition(RideStatus.REQUESTED, RideStatus.MATCHED));
        assertTrue(RideStatus.isValidTransition(RideStatus.MATCHED, RideStatus.ACCEPTED));
        assertTrue(RideStatus.isValidTransition(RideStatus.ACCEPTED, RideStatus.ARRIVED));
        assertTrue(RideStatus.isValidTransition(RideStatus.ARRIVED, RideStatus.STARTED));
        assertTrue(RideStatus.isValidTransition(RideStatus.STARTED, RideStatus.COMPLETED));

        // Assert invalid transitions are rejected
        assertFalse(RideStatus.isValidTransition(RideStatus.REQUESTED, RideStatus.STARTED));
        assertFalse(RideStatus.isValidTransition(RideStatus.COMPLETED, RideStatus.STARTED));
    }
}
