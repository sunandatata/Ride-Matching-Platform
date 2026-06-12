package com.rideshare.driver.controller;

import com.rideshare.driver.dto.*;
import com.rideshare.driver.entity.Document;
import com.rideshare.driver.service.DriverService;
import com.rideshare.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Driver Service.
 * Handles HTTP requests for driver profile management, licensing, vehicles, and documents.
 */
@Slf4j
@RestController
@RequestMapping("/drivers")
@Validated
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    /**
     * Register a new driver.
     *
     * @param request the registration request
     * @return the created driver
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DriverResponse>> registerDriver(
            @Valid @RequestBody DriverRegistrationRequest request) {
        log.info("POST /drivers - Register new driver");

        DriverResponse response = driverService.registerDriver(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Driver registered successfully", response));
    }

    /**
     * Get driver profile by ID.
     *
     * @param driverId the driver ID
     * @return the driver profile
     */
    @GetMapping("/{driverId}")
    public ResponseEntity<ApiResponse<DriverResponse>> getDriver(
            @PathVariable UUID driverId) {
        log.info("GET /drivers/{} - Get driver profile", driverId);

        DriverResponse response = driverService.getDriver(driverId);

        return ResponseEntity.ok(ApiResponse.success("Driver retrieved successfully", response));
    }

    /**
     * Update driver profile.
     *
     * @param driverId the driver ID
     * @param request the update request
     * @return the updated driver
     */
    @PutMapping("/{driverId}")
    public ResponseEntity<ApiResponse<DriverResponse>> updateDriver(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverUpdateRequest request) {
        log.info("PUT /drivers/{} - Update driver profile", driverId);

        DriverResponse response = driverService.updateDriver(
            driverId,
            request.firstName(),
            request.lastName(),
            request.email(),
            request.profilePhotoUrl()
        );

        return ResponseEntity.ok(ApiResponse.success("Driver updated successfully", response));
    }

    /**
     * Update driver vehicle information.
     *
     * @param driverId the driver ID
     * @param request the vehicle update request
     * @return the updated driver
     */
    @PutMapping("/{driverId}/vehicle")
    public ResponseEntity<ApiResponse<DriverResponse>> updateVehicle(
            @PathVariable UUID driverId,
            @Valid @RequestBody VehicleUpdateRequest request) {
        log.info("PUT /drivers/{}/vehicle - Update vehicle", driverId);

        DriverResponse response = driverService.updateVehicle(driverId, request);

        return ResponseEntity.ok(ApiResponse.success("Vehicle updated successfully", response));
    }

    /**
     * Update driver availability status.
     *
     * @param driverId the driver ID
     * @param request the availability status update request
     * @return the updated driver
     */
    @PutMapping("/{driverId}/availability-status")
    public ResponseEntity<ApiResponse<DriverResponse>> updateAvailabilityStatus(
            @PathVariable UUID driverId,
            @Valid @RequestBody AvailabilityStatusUpdateRequest request) {
        log.info("PUT /drivers/{}/availability-status - Update availability status", driverId);

        DriverResponse response = driverService.updateAvailabilityStatus(driverId, request);

        return ResponseEntity.ok(ApiResponse.success("Availability status updated successfully", response));
    }

    /**
     * Update driver last activity timestamp.
     * Called by Location Service or client apps to update driver's last seen time.
     *
     * @param driverId the driver ID
     * @return success response
     */
    @PutMapping("/{driverId}/last-activity")
    public ResponseEntity<ApiResponse<Void>> updateLastActivity(
            @PathVariable UUID driverId) {
        log.info("PUT /drivers/{}/last-activity - Update last activity", driverId);

        driverService.updateLastActivity(driverId);

        return ResponseEntity.ok(ApiResponse.success("Last activity updated successfully", null));
    }

    /**
     * Upload or update driver document.
     *
     * @param driverId the driver ID
     * @param request the document upload request
     * @return the document ID
     */
    @PostMapping("/{driverId}/documents")
    public ResponseEntity<ApiResponse<String>> uploadDocument(
            @PathVariable UUID driverId,
            @Valid @RequestBody DocumentUploadRequest request) {
        log.info("POST /drivers/{}/documents - Upload document of type: {}", driverId, request.documentType());

        UUID documentId = driverService.uploadDocument(driverId, request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Document uploaded successfully", documentId.toString()));
    }

    /**
     * Get all documents for a driver.
     *
     * @param driverId the driver ID
     * @return list of documents
     */
    @GetMapping("/{driverId}/documents")
    public ResponseEntity<ApiResponse<List<Document>>> getDriverDocuments(
            @PathVariable UUID driverId) {
        log.info("GET /drivers/{}/documents - Get driver documents", driverId);

        List<Document> documents = driverService.getDriverDocuments(driverId);

        return ResponseEntity.ok(ApiResponse.success("Documents retrieved successfully", documents));
    }

    /**
     * Get driver statistics (ratings, acceptance rate, earnings).
     *
     * @param driverId the driver ID
     * @return driver stats
     */
    @GetMapping("/{driverId}/stats")
    public ResponseEntity<ApiResponse<DriverStatsResponse>> getDriverStats(
            @PathVariable UUID driverId) {
        log.info("GET /drivers/{}/stats - Get driver statistics", driverId);

        DriverStatsResponse stats = driverService.getDriverStats(driverId);

        return ResponseEntity.ok(ApiResponse.success("Driver statistics retrieved successfully", stats));
    }

    /**
     * Batch driver lookup endpoint.
     * Critical for Matching Engine to fetch driver details for multiple drivers at once.
     *
     * @param request the batch lookup request
     * @return list of drivers
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> getBatchDrivers(
            @Valid @RequestBody BatchDriverLookupRequest request) {
        log.info("POST /drivers/batch - Batch lookup for {} drivers", request.driverIds().size());

        List<DriverResponse> drivers = driverService.getBatchDrivers(request.driverIds());

        return ResponseEntity.ok(ApiResponse.success(
            "Batch driver lookup completed successfully",
            drivers
        ));
    }

    /**
     * Error handler for validation errors.
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation error: " + ex.getMessage()));
    }

    /**
     * Error handler for not found errors.
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(java.util.NoSuchElementException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }
}
