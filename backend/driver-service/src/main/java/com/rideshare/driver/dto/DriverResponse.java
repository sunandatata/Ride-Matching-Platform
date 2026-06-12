package com.rideshare.driver.dto;

import com.rideshare.driver.entity.Driver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for driver profile response.
 * Returns all driver information without sensitive fields.
 */
public record DriverResponse(
    UUID driverId,
    String phoneNumber,
    String email,
    String firstName,
    String lastName,
    String profilePhotoUrl,
    LocalDate dateOfBirth,
    String status,
    String licenseNumber,
    String licenseState,
    LocalDate licenseExpiryDate,
    Boolean licenseVerified,
    String backgroundCheckStatus,
    LocalDate backgroundCheckExpiresDate,
    UUID vehicleId,
    String vehicleMake,
    String vehicleModel,
    Integer vehicleYear,
    String vehicleColor,
    String vehicleLicensePlate,
    Integer vehicleCapacity,
    String vehicleType,
    String vehicleInspectionStatus,
    LocalDate vehicleInspectionDate,
    String availabilityStatus,
    LocalDateTime lastActivityAt,
    BigDecimal averageRating,
    Integer totalRides,
    BigDecimal totalEarnings,
    BigDecimal acceptanceRate,
    BigDecimal cancellationRate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * Convert Driver entity to DriverResponse DTO.
     *
     * @param driver the driver entity
     * @return the driver response
     */
    public static DriverResponse fromEntity(Driver driver) {
        return new DriverResponse(
            driver.getDriverId(),
            driver.getPhoneNumber(),
            driver.getEmail(),
            driver.getFirstName(),
            driver.getLastName(),
            driver.getProfilePhotoUrl(),
            driver.getDateOfBirth(),
            driver.getStatus().name(),
            driver.getLicenseNumber(),
            driver.getLicenseState(),
            driver.getLicenseExpiryDate(),
            driver.getLicenseVerified(),
            driver.getBackgroundCheckStatus().name(),
            driver.getBackgroundCheckExpiresDate(),
            driver.getVehicleId(),
            driver.getVehicleMake(),
            driver.getVehicleModel(),
            driver.getVehicleYear(),
            driver.getVehicleColor(),
            driver.getVehicleLicensePlate(),
            driver.getVehicleCapacity(),
            driver.getVehicleType().name(),
            driver.getVehicleInspectionStatus().name(),
            driver.getVehicleInspectionDate(),
            driver.getAvailabilityStatus().name(),
            driver.getLastActivityAt(),
            driver.getAverageRating(),
            driver.getTotalRides(),
            driver.getTotalEarnings(),
            driver.getAcceptanceRate(),
            driver.getCancellationRate(),
            driver.getCreatedAt(),
            driver.getUpdatedAt()
        );
    }
}
