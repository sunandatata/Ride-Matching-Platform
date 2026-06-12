package com.rideshare.driver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Driver entity representing a driver profile in the system.
 * Manages driver personal info, licensing, vehicle, availability status, and performance metrics.
 */
@Entity
@Table(name = "drivers", indexes = {
    @Index(name = "idx_drivers_phone", columnList = "phone_number", unique = true),
    @Index(name = "idx_drivers_email", columnList = "email"),
    @Index(name = "idx_drivers_status", columnList = "status"),
    @Index(name = "idx_drivers_availability", columnList = "availability_status"),
    @Index(name = "idx_drivers_license", columnList = "license_number"),
    @Index(name = "idx_drivers_vehicle_plate", columnList = "vehicle_license_plate"),
    @Index(name = "idx_drivers_created_at", columnList = "created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID driverId;

    @Column(nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(length = 255, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 500)
    private String profilePhotoUrl;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DriverStatus status = DriverStatus.ACTIVE;

    // License Information
    @Column(nullable = false, unique = true, length = 50)
    private String licenseNumber;

    @Column(length = 50)
    private String licenseState;

    @Column(nullable = false)
    private LocalDate licenseExpiryDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean licenseVerified = false;

    @Column
    private LocalDateTime licenseVerifiedAt;

    // Background Check
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BackgroundCheckStatus backgroundCheckStatus = BackgroundCheckStatus.PENDING;

    @Column
    private LocalDate backgroundCheckDate;

    @Column
    private LocalDate backgroundCheckExpiresDate;

    // Vehicle Information
    @Column(columnDefinition = "UUID")
    private UUID vehicleId;

    @Column(length = 100)
    private String vehicleMake;

    @Column(length = 100)
    private String vehicleModel;

    @Column
    private Integer vehicleYear;

    @Column(length = 50)
    private String vehicleColor;

    @Column(unique = true, length = 50)
    private String vehicleLicensePlate;

    @Column
    @Builder.Default
    private Integer vehicleCapacity = 4;

    @Enumerated(EnumType.STRING)
    @Column
    @Builder.Default
    private VehicleType vehicleType = VehicleType.ECONOMY;

    @Enumerated(EnumType.STRING)
    @Column
    @Builder.Default
    private VehicleInspectionStatus vehicleInspectionStatus = VehicleInspectionStatus.PENDING;

    @Column
    private LocalDate vehicleInspectionDate;

    // Availability Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.OFFLINE;

    @Column
    private LocalDateTime lastActivityAt;

    // Rating and Performance Metrics
    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column
    @Builder.Default
    private Integer totalRides = 0;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal acceptanceRate = new BigDecimal("100.00");

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal cancellationRate = BigDecimal.ZERO;

    // Payment Account
    @Column(length = 255)
    private String paymentAccountToken;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    public enum DriverStatus {
        ACTIVE, SUSPENDED, DELETED
    }

    public enum BackgroundCheckStatus {
        PENDING, APPROVED, FAILED, EXPIRED
    }

    public enum VehicleType {
        ECONOMY, PREMIUM, SHARED
    }

    public enum VehicleInspectionStatus {
        PENDING, APPROVED, FAILED, EXPIRED
    }

    public enum AvailabilityStatus {
        ONLINE, OFFLINE, ON_RIDE, BREAK
    }

    /**
     * Validate license expiry date.
     *
     * @return true if license is valid (not expired)
     */
    public boolean isLicenseValid() {
        return licenseVerified && !licenseExpiryDate.isBefore(LocalDate.now());
    }

    /**
     * Validate background check status.
     *
     * @return true if background check is approved and not expired
     */
    public boolean isBackgroundCheckValid() {
        return backgroundCheckStatus == BackgroundCheckStatus.APPROVED &&
               (backgroundCheckExpiresDate == null || !backgroundCheckExpiresDate.isBefore(LocalDate.now()));
    }

    /**
     * Validate vehicle inspection status.
     *
     * @return true if vehicle inspection is approved
     */
    public boolean isVehicleInspectionValid() {
        return vehicleInspectionStatus == VehicleInspectionStatus.APPROVED &&
               (vehicleInspectionDate == null || !vehicleInspectionDate.plusYears(1).isBefore(LocalDate.now()));
    }

    /**
     * Check if driver is eligible to accept rides.
     *
     * @return true if all requirements are met
     */
    public boolean isEligibleForRides() {
        return status == DriverStatus.ACTIVE &&
               isLicenseValid() &&
               isBackgroundCheckValid() &&
               isVehicleInspectionValid() &&
               availabilityStatus == AvailabilityStatus.ONLINE;
    }
}
