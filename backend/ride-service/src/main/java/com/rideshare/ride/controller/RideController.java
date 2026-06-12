package com.rideshare.ride.controller;

import com.rideshare.ride.dto.*;
import com.rideshare.ride.service.RideService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/**
 * REST controller for ride operations.
 * Implements all ride endpoints as specified in the API contract.
 * Thin controller that delegates business logic to RideService.
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    /**
     * POST /rides - Create a new ride request.
     * Called by rider when requesting a new ride.
     *
     * @param request the ride creation request
     * @return the created ride with 201 Created status
     */
    @PostMapping("/rides")
    public ResponseEntity<RideResponse> createRide(@Valid @RequestBody CreateRideRequest request) {
        log.info("Received request to create ride for rider: {}", request.getRiderId());
        RideResponse response = rideService.createRide(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /rides/{ride_id} - Get ride details.
     * Can be called by rider, driver, or system.
     *
     * @param rideId the ride ID
     * @return the ride response
     */
    @GetMapping("/rides/{rideId}")
    public ResponseEntity<RideResponse> getRide(@PathVariable("rideId") String rideId) {
        log.info("Fetching ride details: {}", rideId);
        RideResponse response = rideService.getRide(rideId);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /rides/{ride_id}/status - Update ride status.
     * Used for state transitions: REQUESTED->MATCHED->ACCEPTED->ARRIVED->STARTED->COMPLETED
     *
     * @param rideId the ride ID
     * @param request the status update request
     * @return the updated ride response
     */
    @PutMapping("/rides/{rideId}/status")
    public ResponseEntity<RideResponse> updateRideStatus(
            @PathVariable("rideId") String rideId,
            @Valid @RequestBody StatusUpdateRequest request) {
        log.info("Updating ride status: ride_id={}, new_status={}", rideId, request.getStatus());
        RideResponse response = rideService.updateRideStatus(rideId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /rides/{ride_id}/driver - Assign a driver to a ride.
     * Called by Matching Engine when a driver is selected.
     * Transitions ride from REQUESTED to MATCHED state.
     *
     * @param rideId the ride ID
     * @param request the driver assignment request
     * @return the updated ride response
     */
    @PutMapping("/rides/{rideId}/driver")
    public ResponseEntity<RideResponse> assignDriver(
            @PathVariable("rideId") String rideId,
            @Valid @RequestBody AssignDriverRequest request) {
        log.info("Assigning driver to ride: ride_id={}, driver_id={}", rideId, request.getDriverId());
        RideResponse response = rideService.assignDriver(rideId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /rides/{ride_id}/cancel - Cancel a ride.
     * Can be initiated by rider, driver, or system.
     *
     * @param rideId the ride ID
     * @param request the cancellation request
     * @return the cancelled ride response
     */
    @PostMapping("/rides/{rideId}/cancel")
    public ResponseEntity<RideResponse> cancelRide(
            @PathVariable("rideId") String rideId,
            @Valid @RequestBody CancelRideRequest request) {
        log.info("Cancelling ride: ride_id={}, reason={}", rideId, request.getReason());
        RideResponse response = rideService.cancelRide(rideId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /rides/{ride_id}/complete - Mark a ride as completed.
     * Must provide the actual fare charged.
     * Transitions to COMPLETED state and processes payment.
     *
     * @param rideId the ride ID
     * @param actualFare the actual fare amount
     * @return the completed ride response
     */
    @PostMapping("/rides/{rideId}/complete")
    public ResponseEntity<RideResponse> completeRide(
            @PathVariable("rideId") String rideId,
            @RequestParam("actualFare") BigDecimal actualFare) {
        log.info("Completing ride: ride_id={}, actual_fare={}", rideId, actualFare);
        RideResponse response = rideService.completeRide(rideId, actualFare);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /rides/{ride_id}/rating - Rate a completed ride.
     * Both rider and driver can provide ratings and feedback.
     *
     * @param rideId the ride ID
     * @param request the rating request
     * @return the updated ride response
     */
    @PostMapping("/rides/{rideId}/rating")
    public ResponseEntity<RideResponse> rateRide(
            @PathVariable("rideId") String rideId,
            @Valid @RequestBody RatingRequest request) {
        log.info("Rating ride: ride_id={}, rater_type={}, rating={}", rideId, request.getRaterType(), request.getRating());
        RideResponse response = rideService.rateRide(rideId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /riders/{rider_id}/rides - Get all rides for a specific rider.
     * Paginated response for ride history.
     *
     * @param riderId the rider ID
     * @param pageable pagination info
     * @return page of rider's rides
     */
    @GetMapping("/riders/{riderId}/rides")
    public ResponseEntity<Page<RideResponse>> getRiderRides(
            @PathVariable("riderId") String riderId,
            Pageable pageable) {
        log.info("Fetching rides for rider: {}", riderId);
        Page<RideResponse> response = rideService.getRiderRides(riderId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /drivers/{driver_id}/rides - Get all rides for a specific driver.
     * Paginated response for driver's history.
     *
     * @param driverId the driver ID
     * @param pageable pagination info
     * @return page of driver's rides
     */
    @GetMapping("/drivers/{driverId}/rides")
    public ResponseEntity<Page<RideResponse>> getDriverRides(
            @PathVariable("driverId") String driverId,
            Pageable pageable) {
        log.info("Fetching rides for driver: {}", driverId);
        Page<RideResponse> response = rideService.getDriverRides(driverId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /rides/{ride_id}/events - Get all events for a ride.
     * Used for debugging and audit trail.
     * Shows complete history of state changes.
     *
     * @param rideId the ride ID
     * @param pageable pagination info
     * @return page of ride events
     */
    @GetMapping("/rides/{rideId}/events")
    public ResponseEntity<Page<RideEventResponse>> getRideEvents(
            @PathVariable("rideId") String rideId,
            Pageable pageable) {
        log.info("Fetching events for ride: {}", rideId);
        Page<RideEventResponse> response = rideService.getRideEvents(rideId, pageable);
        return ResponseEntity.ok(response);
    }
}
