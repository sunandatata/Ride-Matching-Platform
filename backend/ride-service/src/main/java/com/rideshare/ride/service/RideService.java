package com.rideshare.ride.service;

import com.rideshare.ride.dto.*;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.entity.RideEvent;
import com.rideshare.ride.entity.RideEventType;
import com.rideshare.ride.entity.RideStatus;
import com.rideshare.ride.exception.InvalidStateTransitionException;
import com.rideshare.ride.exception.RideNotFoundException;
import com.rideshare.ride.repository.RideEventRepository;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.ride.validator.RideStateValidator;
import com.rideshare.ride.event.RideEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core service implementing ride lifecycle management and state machine.
 * Handles all ride operations with transactional consistency.
 *
 * This is the critical service that ensures:
 * 1. State machine integrity (no invalid transitions)
 * 2. Atomic state changes with event publishing
 * 3. Event sourcing for audit trail
 * 4. Sharding integration for scalability
 */
@Service
@Slf4j
public class RideService {

    private static final int SHARD_COUNT = 8;
    private static final int MATCH_EXPIRY_SECONDS = 30;

    private final RideRepository rideRepository;
    private final RideEventRepository eventRepository;
    private final RideStateValidator stateValidator;
    private final RideEventPublisher eventPublisher;
    private final PaymentProcessor paymentProcessor;

    public RideService(
            RideRepository rideRepository,
            RideEventRepository eventRepository,
            RideStateValidator stateValidator,
            RideEventPublisher eventPublisher,
            PaymentProcessor paymentProcessor) {
        this.rideRepository = rideRepository;
        this.eventRepository = eventRepository;
        this.stateValidator = stateValidator;
        this.eventPublisher = eventPublisher;
        this.paymentProcessor = paymentProcessor;
    }

    /**
     * Creates a new ride request.
     * Validates input, generates ride ID, sets initial state to REQUESTED.
     * Published ride.requested event to Kafka.
     *
     * Performance target: <100ms
     *
     * @param request the ride creation request with validation
     * @return the created ride response
     */
    @Transactional
    public RideResponse createRide(CreateRideRequest request) {
        log.info("Creating new ride for rider: {}", request.getRiderId());

        // Generate unique ride ID
        String rideId = generateRideId();
        int shardId = calculateShardId(rideId);

        // Create ride entity in REQUESTED state
        Ride ride = Ride.builder()
                .id(rideId)
                .riderId(request.getRiderId())
                .status(RideStatus.REQUESTED)
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .pickupAddress(request.getPickupAddress())
                .dropoffLatitude(request.getDropoffLatitude())
                .dropoffLongitude(request.getDropoffLongitude())
                .dropoffAddress(request.getDropoffAddress())
                .passengerCount(request.getPassengerCount())
                .shardId(shardId)
                .build();

        // Save ride to database
        ride = rideRepository.save(ride);
        log.info("Ride created successfully: id={}, shard={}", rideId, shardId);

        // Record event in event store
        recordEvent(ride, RideEventType.RIDE_REQUESTED, null, RideStatus.REQUESTED, null, null);

        // Publish event to Kafka for other services
        eventPublisher.publishRideRequested(ride);

        return mapToResponse(ride);
    }

    /**
     * Retrieves a ride by ID.
     *
     * @param rideId the ride ID
     * @return the ride response
     * @throws RideNotFoundException if ride is not found
     */
    @Transactional(readOnly = true)
    public RideResponse getRide(String rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));
        return mapToResponse(ride);
    }

    /**
     * Assigns a driver to a ride (called by Matching Engine).
     * Validates that ride is in REQUESTED state.
     * Transitions to MATCHED state with expiry time.
     *
     * @param rideId the ride ID
     * @param request the driver assignment request
     * @return the updated ride response
     * @throws RideNotFoundException if ride is not found
     * @throws InvalidStateTransitionException if ride is not in REQUESTED state
     */
    @Transactional
    public RideResponse assignDriver(String rideId, AssignDriverRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        log.info("Assigning driver {} to ride {}", request.getDriverId(), rideId);

        // Validate the ride can accept a driver
        stateValidator.validateCanAssignDriver(ride);

        // Update ride with driver information
        ride.setDriverId(request.getDriverId());
        ride.setEstimatedFare(request.getEstimatedFare());
        ride.setEstimatedDurationSeconds(request.getEstimatedDurationSeconds());
        ride.setDriverETA(request.getDriverETA());

        // Transition to MATCHED state
        ride.setStatus(RideStatus.MATCHED);
        ride.setMatchedAt(LocalDateTime.now());

        // Save and log
        ride = rideRepository.save(ride);
        log.info("Ride matched successfully: driver_id={}, estimated_fare={}",
            request.getDriverId(), request.getEstimatedFare());

        // Record event
        recordEvent(ride, RideEventType.RIDE_MATCHED, null, RideStatus.REQUESTED, RideStatus.MATCHED, request.getDriverId());

        // Publish event
        eventPublisher.publishRideMatched(ride);

        return mapToResponse(ride);
    }

    /**
     * Updates ride status through the state machine.
     * Validates transitions and handles side effects based on the new state.
     *
     * @param rideId the ride ID
     * @param request the status update request
     * @return the updated ride response
     * @throws RideNotFoundException if ride is not found
     * @throws InvalidStateTransitionException if transition is invalid
     */
    @Transactional
    public RideResponse updateRideStatus(String rideId, StatusUpdateRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        RideStatus targetStatus = request.getStatus();
        RideStatus previousStatus = ride.getStatus();

        log.info("Updating ride status: ride_id={}, from={}, to={}", rideId, previousStatus, targetStatus);

        // Validate the transition
        stateValidator.validateStateTransition(ride, targetStatus);
        stateValidator.validateTransitionPrerequisites(ride, targetStatus);

        // Handle state-specific logic
        handleStateTransition(ride, targetStatus, request.getInitiatorId(), request.getInitiatorType());

        // Save the updated ride
        ride = rideRepository.save(ride);

        // Record event
        recordEvent(ride, getEventTypeForStatus(targetStatus), request.getInitiatorId(), previousStatus, targetStatus, null);

        // Publish appropriate Kafka event
        publishStatusChangeEvent(ride);

        log.info("Ride status updated successfully: ride_id={}, new_status={}", rideId, targetStatus);

        return mapToResponse(ride);
    }

    /**
     * Handles the specific logic for each state transition.
     * Side effects like time updates and validations happen here.
     *
     * @param ride the ride entity
     * @param targetStatus the target status
     * @param initiatorId the ID of who initiated the change
     * @param initiatorType the type of initiator (RIDER, DRIVER, SYSTEM)
     */
    private void handleStateTransition(Ride ride, RideStatus targetStatus, String initiatorId, String initiatorType) {
        switch (targetStatus) {
            case ACCEPTED:
                ride.setStatus(RideStatus.ACCEPTED);
                ride.setAcceptedAt(LocalDateTime.now());
                break;

            case ARRIVED:
                ride.setStatus(RideStatus.ARRIVED);
                ride.setArrivedAt(LocalDateTime.now());
                break;

            case STARTED:
                ride.setStatus(RideStatus.STARTED);
                ride.setStartedAt(LocalDateTime.now());
                break;

            case COMPLETED:
                ride.setStatus(RideStatus.COMPLETED);
                ride.setCompletedAt(LocalDateTime.now());
                // Calculate actual duration
                if (ride.getStartedAt() != null && ride.getCompletedAt() != null) {
                    long durationSeconds = java.time.temporal.ChronoUnit.SECONDS
                        .between(ride.getStartedAt(), ride.getCompletedAt());
                    ride.setActualDurationSeconds((int) durationSeconds);
                }
                break;

            case CANCELLED:
                ride.setStatus(RideStatus.CANCELLED);
                ride.setCancelledAt(LocalDateTime.now());
                ride.setCancellationInitiator(initiatorType);
                break;

            default:
                log.warn("No specific handling for status: {}", targetStatus);
        }
    }

    /**
     * Cancels a ride with a reason.
     * Can be initiated by rider, driver, or system.
     * Handles refunds if payment was already processed.
     *
     * @param rideId the ride ID
     * @param request the cancellation request
     * @return the cancelled ride response
     * @throws RideNotFoundException if ride is not found
     */
    @Transactional
    public RideResponse cancelRide(String rideId, CancelRideRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        log.info("Cancelling ride {}: reason={}, initiated_by={}",
            rideId, request.getReason(), request.getInitiatorType());

        // Validate cancellation is allowed
        stateValidator.validateCanCancel(ride);

        RideStatus previousStatus = ride.getStatus();

        // Update ride status
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(LocalDateTime.now());
        ride.setCancellationReason(request.getReason());
        ride.setCancellationInitiator(request.getInitiatorType());

        // Save cancelled ride
        ride = rideRepository.save(ride);

        // Record event
        recordEvent(ride, RideEventType.RIDE_CANCELLED, request.getInitiatorId(), previousStatus, RideStatus.CANCELLED, null);

        // Publish cancellation event
        eventPublisher.publishRideCancelled(ride);

        log.info("Ride cancelled successfully: ride_id={}, reason={}", rideId, request.getReason());

        return mapToResponse(ride);
    }

    /**
     * Completes a ride and processes payment.
     *
     * @param rideId the ride ID
     * @param actualFare the actual fare charged
     * @return the completed ride response
     * @throws RideNotFoundException if ride is not found
     */
    @Transactional
    public RideResponse completeRide(String rideId, BigDecimal actualFare) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        log.info("Completing ride {}: actual_fare={}", rideId, actualFare);

        // Must be in STARTED state
        if (ride.getStatus() != RideStatus.STARTED) {
            throw new InvalidStateTransitionException(ride.getStatus(), RideStatus.COMPLETED);
        }

        // Update fare and complete
        ride.setActualFare(actualFare);
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());

        // Calculate actual duration
        if (ride.getStartedAt() != null) {
            long durationSeconds = java.time.temporal.ChronoUnit.SECONDS
                .between(ride.getStartedAt(), ride.getCompletedAt());
            ride.setActualDurationSeconds((int) durationSeconds);
        }

        // Save ride
        ride = rideRepository.save(ride);

        // Process payment
        boolean paymentSuccess = paymentProcessor.processPayment(ride, actualFare);
        if (!paymentSuccess) {
            log.warn("Payment processing failed for ride {}, but ride marked as completed", rideId);
        }

        // Record event
        recordEvent(ride, RideEventType.RIDE_COMPLETED, null, RideStatus.STARTED, RideStatus.COMPLETED, null);

        // Publish event
        eventPublisher.publishRideCompleted(ride);

        log.info("Ride completed successfully: ride_id={}, actual_fare={}", rideId, actualFare);

        return mapToResponse(ride);
    }

    /**
     * Rates a completed ride.
     * Both rider and driver can provide ratings after completion.
     *
     * @param rideId the ride ID
     * @param request the rating request
     * @return the updated ride response
     * @throws RideNotFoundException if ride is not found
     */
    @Transactional
    public RideResponse rateRide(String rideId, RatingRequest request) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RideNotFoundException(rideId));

        log.info("Rating ride {}: rater_type={}, rating={}", rideId, request.getRaterType(), request.getRating());

        // Validate ride is completed
        stateValidator.validateCanRate(ride);

        // Update ratings based on rater type
        if ("RIDER".equals(request.getRaterType())) {
            ride.setDriverRating(request.getRating());
            ride.setDriverFeedback(request.getFeedback());
        } else if ("DRIVER".equals(request.getRaterType())) {
            ride.setRiderRating(request.getRating());
            ride.setRiderFeedback(request.getFeedback());
        } else {
            throw new IllegalArgumentException("Invalid rater type: " + request.getRaterType());
        }

        // Save and log
        ride = rideRepository.save(ride);
        log.info("Rating recorded for ride {}: rater_type={}, rating={}",
            rideId, request.getRaterType(), request.getRating());

        // Record event
        recordEvent(ride, RideEventType.RATING_PROVIDED, request.getRaterId(), ride.getStatus(), null, null);

        return mapToResponse(ride);
    }

    /**
     * Gets ride history for a specific rider.
     *
     * @param riderId the rider ID
     * @param pageable pagination info
     * @return page of rides for the rider
     */
    @Transactional(readOnly = true)
    public Page<RideResponse> getRiderRides(String riderId, Pageable pageable) {
        return rideRepository.findByRiderId(riderId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Gets ride history for a specific driver.
     *
     * @param driverId the driver ID
     * @param pageable pagination info
     * @return page of rides for the driver
     */
    @Transactional(readOnly = true)
    public Page<RideResponse> getDriverRides(String driverId, Pageable pageable) {
        return rideRepository.findByDriverId(driverId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Gets all events for a ride (audit trail).
     *
     * @param rideId the ride ID
     * @param pageable pagination info
     * @return page of events for the ride
     */
    @Transactional(readOnly = true)
    public Page<RideEventResponse> getRideEvents(String rideId, Pageable pageable) {
        return eventRepository.findByRideIdOrderByCreatedAtAsc(rideId, pageable)
                .map(this::mapEventToResponse);
    }

    /**
     * Records a ride event in the event store for audit trail.
     * Implements event sourcing pattern.
     *
     * @param ride the ride entity
     * @param eventType the type of event
     * @param initiatorId the ID of who initiated the event
     * @param previousStatus the previous ride status
     * @param newStatus the new ride status
     * @param eventData additional event data
     */
    private void recordEvent(Ride ride, RideEventType eventType, String initiatorId,
                           RideStatus previousStatus, RideStatus newStatus, String eventData) {
        RideEvent event = RideEvent.builder()
                .rideId(ride.getId())
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .initiatorId(initiatorId)
                .initiatorType("SYSTEM") // Default to SYSTEM
                .eventData(eventData)
                .build();

        eventRepository.save(event);
        log.debug("Event recorded for ride {}: type={}", ride.getId(), eventType);
    }

    /**
     * Publishes the appropriate Kafka event based on ride status.
     *
     * @param ride the ride entity
     */
    private void publishStatusChangeEvent(Ride ride) {
        switch (ride.getStatus()) {
            case MATCHED:
                eventPublisher.publishRideMatched(ride);
                break;
            case ACCEPTED:
                eventPublisher.publishRideAccepted(ride);
                break;
            case ARRIVED:
                eventPublisher.publishRideArrived(ride);
                break;
            case STARTED:
                eventPublisher.publishRideStarted(ride);
                break;
            case COMPLETED:
                eventPublisher.publishRideCompleted(ride);
                break;
            case CANCELLED:
                eventPublisher.publishRideCancelled(ride);
                break;
            default:
                log.warn("No Kafka event defined for status: {}", ride.getStatus());
        }
    }

    /**
     * Maps event entity to response DTO.
     *
     * @param event the ride event
     * @return the event response DTO
     */
    private RideEventResponse mapEventToResponse(RideEvent event) {
        return RideEventResponse.builder()
                .id(event.getId())
                .rideId(event.getRideId())
                .eventType(event.getEventType())
                .previousStatus(event.getPreviousStatus())
                .newStatus(event.getNewStatus())
                .initiatorId(event.getInitiatorId())
                .initiatorType(event.getInitiatorType())
                .eventData(event.getEventData())
                .createdAt(event.getCreatedAt())
                .build();
    }

    /**
     * Maps ride entity to response DTO.
     *
     * @param ride the ride entity
     * @return the ride response DTO
     */
    private RideResponse mapToResponse(Ride ride) {
        return RideResponse.builder()
                .id(ride.getId())
                .riderId(ride.getRiderId())
                .driverId(ride.getDriverId())
                .status(ride.getStatus())
                .pickupLatitude(ride.getPickupLatitude())
                .pickupLongitude(ride.getPickupLongitude())
                .pickupAddress(ride.getPickupAddress())
                .dropoffLatitude(ride.getDropoffLatitude())
                .dropoffLongitude(ride.getDropoffLongitude())
                .dropoffAddress(ride.getDropoffAddress())
                .passengerCount(ride.getPassengerCount())
                .estimatedFare(ride.getEstimatedFare())
                .actualFare(ride.getActualFare())
                .estimatedDurationSeconds(ride.getEstimatedDurationSeconds())
                .actualDurationSeconds(ride.getActualDurationSeconds())
                .driverETA(ride.getDriverETA())
                .matchedAt(ride.getMatchedAt())
                .acceptedAt(ride.getAcceptedAt())
                .arrivedAt(ride.getArrivedAt())
                .startedAt(ride.getStartedAt())
                .completedAt(ride.getCompletedAt())
                .cancelledAt(ride.getCancelledAt())
                .cancellationReason(ride.getCancellationReason())
                .cancellationInitiator(ride.getCancellationInitiator())
                .createdAt(ride.getCreatedAt())
                .updatedAt(ride.getUpdatedAt())
                .driverRating(ride.getDriverRating())
                .riderRating(ride.getRiderRating())
                .driverFeedback(ride.getDriverFeedback())
                .riderFeedback(ride.getRiderFeedback())
                .build();
    }

    /**
     * Generates a unique ride ID using UUID.
     * Combined with sharding logic for distribution.
     *
     * @return unique ride ID
     */
    private String generateRideId() {
        return "RIDE-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Calculates shard ID for a ride using consistent hashing.
     * Distribution: ride_id hash % 8 (8 shards total).
     *
     * @param rideId the ride ID
     * @return shard ID (0-7)
     */
    private int calculateShardId(String rideId) {
        return Math.abs(rideId.hashCode()) % SHARD_COUNT;
    }

    /**
     * Maps ride status to event type.
     *
     * @param status the ride status
     * @return the corresponding event type
     */
    private RideEventType getEventTypeForStatus(RideStatus status) {
        return switch (status) {
            case REQUESTED -> RideEventType.RIDE_REQUESTED;
            case MATCHED -> RideEventType.RIDE_MATCHED;
            case ACCEPTED -> RideEventType.RIDE_ACCEPTED;
            case ARRIVED -> RideEventType.RIDE_ARRIVED;
            case STARTED -> RideEventType.RIDE_STARTED;
            case COMPLETED -> RideEventType.RIDE_COMPLETED;
            case CANCELLED -> RideEventType.RIDE_CANCELLED;
        };
    }
}
