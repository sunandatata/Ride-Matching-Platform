package com.rideshare.driver.service;

import com.rideshare.driver.dto.*;
import com.rideshare.driver.entity.Driver;
import com.rideshare.driver.entity.Document;
import com.rideshare.driver.repository.DriverRepository;
import com.rideshare.driver.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for driver profile management.
 * Handles registration, profile updates, availability status, and document management.
 */
@Slf4j
@Service
public class DriverService {

    private static final String DRIVER_CACHE_KEY_PREFIX = "driver:";
    private static final String DRIVER_STATS_CACHE_KEY_PREFIX = "driver_stats:";
    private static final long CACHE_TTL_MINUTES = 30;

    private final DriverRepository driverRepository;
    private final DocumentRepository documentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public DriverService(
            DriverRepository driverRepository,
            DocumentRepository documentRepository,
            RedisTemplate<String, Object> redisTemplate) {
        this.driverRepository = driverRepository;
        this.documentRepository = documentRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Register a new driver.
     * Validates all required information and creates driver record.
     *
     * @param request the registration request
     * @return the created driver response
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public DriverResponse registerDriver(DriverRegistrationRequest request) {
        log.info("Registering new driver with phone: {}", request.phoneNumber());

        // Check for duplicate phone number
        if (driverRepository.findByPhoneNumber(request.phoneNumber()).isPresent()) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        // Check for duplicate license number
        if (driverRepository.findByLicenseNumber(request.licenseNumber()).isPresent()) {
            throw new IllegalArgumentException("License number already registered");
        }

        // Check for duplicate vehicle license plate
        if (driverRepository.findByVehicleLicensePlate(request.vehicleLicensePlate()).isPresent()) {
            throw new IllegalArgumentException("Vehicle license plate already registered");
        }

        // Validate age (must be at least 18)
        LocalDate minAgeDate = LocalDate.now().minusYears(18);
        if (request.dateOfBirth().isAfter(minAgeDate)) {
            throw new IllegalArgumentException("Driver must be at least 18 years old");
        }

        // Create driver entity
        Driver driver = Driver.builder()
            .driverId(UUID.randomUUID())
            .phoneNumber(request.phoneNumber())
            .email(request.email())
            .firstName(request.firstName())
            .lastName(request.lastName())
            .dateOfBirth(request.dateOfBirth())
            .licenseNumber(request.licenseNumber())
            .licenseState(request.licenseState())
            .licenseExpiryDate(request.licenseExpiryDate())
            .vehicleMake(request.vehicleMake())
            .vehicleModel(request.vehicleModel())
            .vehicleYear(request.vehicleYear())
            .vehicleColor(request.vehicleColor())
            .vehicleLicensePlate(request.vehicleLicensePlate())
            .vehicleCapacity(request.vehicleCapacity())
            .vehicleId(UUID.randomUUID())
            .build();

        Driver savedDriver = driverRepository.save(driver);
        log.info("Driver registered successfully with ID: {}", savedDriver.getDriverId());

        return DriverResponse.fromEntity(savedDriver);
    }

    /**
     * Get driver profile by ID.
     * Uses caching to reduce database queries.
     *
     * @param driverId the driver ID
     * @return the driver response
     * @throws NoSuchElementException if driver not found
     */
    @Transactional(readOnly = true)
    public DriverResponse getDriver(UUID driverId) {
        String cacheKey = DRIVER_CACHE_KEY_PREFIX + driverId;

        // Try to get from cache first
        DriverResponse cached = (DriverResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Driver cache hit for ID: {}", driverId);
            return cached;
        }

        log.debug("Driver cache miss for ID: {}, fetching from database", driverId);
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new NoSuchElementException("Driver not found: " + driverId));

        DriverResponse response = DriverResponse.fromEntity(driver);

        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return response;
    }

    /**
     * Update driver profile.
     *
     * @param driverId the driver ID
     * @param firstName the first name
     * @param lastName the last name
     * @param email the email
     * @param profilePhotoUrl the profile photo URL
     * @return the updated driver response
     */
    @Transactional
    public DriverResponse updateDriver(UUID driverId, String firstName, String lastName, String email, String profilePhotoUrl) {
        log.info("Updating driver profile for ID: {}", driverId);

        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new NoSuchElementException("Driver not found: " + driverId));

        if (firstName != null && !firstName.isBlank()) {
            driver.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            driver.setLastName(lastName);
        }
        if (email != null && !email.isBlank()) {
            driver.setEmail(email);
        }
        if (profilePhotoUrl != null && !profilePhotoUrl.isBlank()) {
            driver.setProfilePhotoUrl(profilePhotoUrl);
        }

        Driver updatedDriver = driverRepository.save(driver);
        invalidateDriverCache(driverId);

        log.info("Driver profile updated for ID: {}", driverId);
        return DriverResponse.fromEntity(updatedDriver);
    }

    /**
     * Update vehicle information.
     *
     * @param driverId the driver ID
     * @param request the vehicle update request
     * @return the updated driver response
     */
    @Transactional
    public DriverResponse updateVehicle(UUID driverId, VehicleUpdateRequest request) {
        log.info("Updating vehicle for driver ID: {}", driverId);

        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new NoSuchElementException("Driver not found: " + driverId));

        // Check if new plate is already in use
        if (!request.vehicleLicensePlate().equals(driver.getVehicleLicensePlate())) {
            if (driverRepository.findByVehicleLicensePlate(request.vehicleLicensePlate()).isPresent()) {
                throw new IllegalArgumentException("Vehicle license plate already registered");
            }
        }

        driver.setVehicleMake(request.vehicleMake());
        driver.setVehicleModel(request.vehicleModel());
        driver.setVehicleYear(request.vehicleYear());
        driver.setVehicleColor(request.vehicleColor());
        driver.setVehicleLicensePlate(request.vehicleLicensePlate());
        driver.setVehicleCapacity(request.vehicleCapacity());
        driver.setVehicleType(Driver.VehicleType.valueOf(request.vehicleType()));

        Driver updatedDriver = driverRepository.save(driver);
        invalidateDriverCache(driverId);

        log.info("Vehicle updated for driver ID: {}", driverId);
        return DriverResponse.fromEntity(updatedDriver);
    }

    /**
     * Update driver availability status.
     * Validates status transitions.
     *
     * @param driverId the driver ID
     * @param request the availability status update request
     * @return the updated driver response
     */
    @Transactional
    public DriverResponse updateAvailabilityStatus(UUID driverId, AvailabilityStatusUpdateRequest request) {
        log.info("Updating availability status for driver ID: {} to {}", driverId, request.status());

        if (!request.isValidStatus()) {
            throw new IllegalArgumentException("Invalid availability status: " + request.status());
        }

        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new NoSuchElementException("Driver not found: " + driverId));

        // Validate transition
        Driver.AvailabilityStatus newStatus = Driver.AvailabilityStatus.valueOf(request.status());
        validateStatusTransition(driver.getAvailabilityStatus(), newStatus, driver);

        driver.setAvailabilityStatus(newStatus);
        driver.setLastActivityAt(LocalDateTime.now());

        Driver updatedDriver = driverRepository.save(driver);
        invalidateDriverCache(driverId);

        log.info("Availability status updated for driver ID: {}", driverId);
        return DriverResponse.fromEntity(updatedDriver);
    }

    /**
     * Update driver last activity timestamp.
     * Called when driver updates location or interacts with platform.
     *
     * @param driverId the driver ID
     */
    @Transactional
    public void updateLastActivity(UUID driverId) {
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new NoSuchElementException("Driver not found: " + driverId));

        driver.setLastActivityAt(LocalDateTime.now());
        driverRepository.save(driver);
        invalidateDriverCache(driverId);
    }

    /**
     * Get batch of drivers by IDs.
     * Critical endpoint for Matching Engine.
     *
     * @param driverIds the list of driver IDs
     * @return list of driver responses
     */
    @Transactional(readOnly = true)
    public List<DriverResponse> getBatchDrivers(List<UUID> driverIds) {
        log.debug("Fetching batch of {} drivers", driverIds.size());

        List<Driver> drivers = driverRepository.findAllById(driverIds);

        if (drivers.size() != driverIds.size()) {
            log.warn("Expected {} drivers but found {}", driverIds.size(), drivers.size());
        }

        return drivers.stream()
            .map(DriverResponse::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Upload or update driver document.
     *
     * @param driverId the driver ID
     * @param request the document upload request
     * @return document ID
     */
    @Transactional
    public UUID uploadDocument(UUID driverId, DocumentUploadRequest request) {
        log.info("Uploading document for driver ID: {} of type: {}", driverId, request.documentType());

        if (!request.isValidDocumentType()) {
            throw new IllegalArgumentException("Invalid document type: " + request.documentType());
        }

        // Check driver exists
        if (!driverRepository.existsById(driverId)) {
            throw new NoSuchElementException("Driver not found: " + driverId);
        }

        Document document = Document.builder()
            .documentId(UUID.randomUUID())
            .driverId(driverId)
            .documentType(Document.DocumentType.valueOf(request.documentType()))
            .documentUrl(request.documentUrl())
            .expiryDate(request.expiryDate())
            .status(Document.DocumentStatus.PENDING)
            .build();

        Document savedDocument = documentRepository.save(document);
        log.info("Document uploaded with ID: {}", savedDocument.getDocumentId());

        return savedDocument.getDocumentId();
    }

    /**
     * Get all documents for a driver.
     *
     * @param driverId the driver ID
     * @return list of documents
     */
    @Transactional(readOnly = true)
    public List<Document> getDriverDocuments(UUID driverId) {
        log.debug("Fetching documents for driver ID: {}", driverId);

        if (!driverRepository.existsById(driverId)) {
            throw new NoSuchElementException("Driver not found: " + driverId);
        }

        return documentRepository.findByDriverId(driverId);
    }

    /**
     * Get driver statistics (ratings, acceptance rate, earnings).
     *
     * @param driverId the driver ID
     * @return driver stats response
     */
    @Transactional(readOnly = true)
    public DriverStatsResponse getDriverStats(UUID driverId) {
        String cacheKey = DRIVER_STATS_CACHE_KEY_PREFIX + driverId;

        // Try to get from cache first
        DriverStatsResponse cached = (DriverStatsResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Driver stats cache hit for ID: {}", driverId);
            return cached;
        }

        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new NoSuchElementException("Driver not found: " + driverId));

        DriverStatsResponse stats = new DriverStatsResponse(
            driver.getDriverId().toString(),
            driver.getAverageRating(),
            driver.getTotalRides(),
            driver.getAcceptanceRate(),
            driver.getCancellationRate(),
            driver.getTotalEarnings()
        );

        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, stats, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return stats;
    }

    /**
     * Update driver performance metrics.
     * Called by Ride Service after ride completion.
     *
     * @param driverId the driver ID
     * @param rating the ride rating (0-5)
     * @param earnings the ride earnings
     * @param accepted whether the ride was accepted
     */
    @Transactional
    public void updateDriverMetrics(UUID driverId, BigDecimal rating, BigDecimal earnings, boolean accepted) {
        log.info("Updating metrics for driver ID: {}", driverId);

        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new NoSuchElementException("Driver not found: " + driverId));

        // Update average rating
        BigDecimal totalRidePoints = driver.getAverageRating()
            .multiply(new BigDecimal(driver.getTotalRides()));
        BigDecimal newAverageRating = totalRidePoints
            .add(rating)
            .divide(new BigDecimal(driver.getTotalRides() + 1), 2, java.math.RoundingMode.HALF_UP);

        driver.setAverageRating(newAverageRating);
        driver.setTotalRides(driver.getTotalRides() + 1);
        driver.setTotalEarnings(driver.getTotalEarnings().add(earnings));

        // Update acceptance rate
        if (accepted) {
            BigDecimal newAcceptanceRate = driver.getAcceptanceRate();
            driver.setAcceptanceRate(newAcceptanceRate);
        } else {
            // Decrease acceptance rate
            BigDecimal decrementedRate = driver.getAcceptanceRate()
                .subtract(new BigDecimal("0.5"))
                .max(BigDecimal.ZERO);
            driver.setAcceptanceRate(decrementedRate);
        }

        driverRepository.save(driver);
        invalidateDriverStats(driverId);
        invalidateDriverCache(driverId);

        log.info("Driver metrics updated for ID: {}", driverId);
    }

    /**
     * Validate status transition based on business rules.
     *
     * @param currentStatus the current status
     * @param newStatus the new status
     * @param driver the driver entity
     * @throws IllegalArgumentException if transition is invalid
     */
    private void validateStatusTransition(
            Driver.AvailabilityStatus currentStatus,
            Driver.AvailabilityStatus newStatus,
            Driver driver) {

        // Only eligible drivers can go online
        if (newStatus == Driver.AvailabilityStatus.ONLINE && !driver.isEligibleForRides()) {
            throw new IllegalArgumentException(
                "Driver is not eligible to go online. Check license, background check, and vehicle inspection."
            );
        }

        // Cannot transition directly from ON_RIDE to ONLINE
        if (currentStatus == Driver.AvailabilityStatus.ON_RIDE && newStatus == Driver.AvailabilityStatus.ONLINE) {
            throw new IllegalArgumentException("Cannot go online while on a ride");
        }
    }

    /**
     * Invalidate driver cache entry.
     *
     * @param driverId the driver ID
     */
    private void invalidateDriverCache(UUID driverId) {
        String cacheKey = DRIVER_CACHE_KEY_PREFIX + driverId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (deleted != null && deleted) {
            log.debug("Driver cache invalidated for ID: {}", driverId);
        }
    }

    /**
     * Invalidate driver stats cache entry.
     *
     * @param driverId the driver ID
     */
    private void invalidateDriverStats(UUID driverId) {
        String cacheKey = DRIVER_STATS_CACHE_KEY_PREFIX + driverId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (deleted != null && deleted) {
            log.debug("Driver stats cache invalidated for ID: {}", driverId);
        }
    }
}
