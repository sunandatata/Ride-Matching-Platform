package com.rideshare.driver.repository;

import com.rideshare.driver.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Driver entity.
 * Provides database access operations for driver profiles.
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    /**
     * Find driver by phone number.
     *
     * @param phoneNumber the phone number
     * @return the driver if found
     */
    Optional<Driver> findByPhoneNumber(String phoneNumber);

    /**
     * Find driver by email.
     *
     * @param email the email
     * @return the driver if found
     */
    Optional<Driver> findByEmail(String email);

    /**
     * Find driver by license number.
     *
     * @param licenseNumber the license number
     * @return the driver if found
     */
    Optional<Driver> findByLicenseNumber(String licenseNumber);

    /**
     * Find driver by vehicle license plate.
     *
     * @param vehicleLicensePlate the vehicle license plate
     * @return the driver if found
     */
    Optional<Driver> findByVehicleLicensePlate(String vehicleLicensePlate);

    /**
     * Find all drivers by availability status.
     *
     * @param availabilityStatus the availability status
     * @return list of drivers
     */
    List<Driver> findByAvailabilityStatus(Driver.AvailabilityStatus availabilityStatus);

    /**
     * Find all active drivers by availability status.
     *
     * @param availabilityStatus the availability status
     * @return list of drivers
     */
    @Query("SELECT d FROM Driver d WHERE d.status = 'ACTIVE' AND d.availabilityStatus = :availabilityStatus")
    List<Driver> findActiveByAvailabilityStatus(@Param("availabilityStatus") Driver.AvailabilityStatus availabilityStatus);

    /**
     * Find all drivers by status.
     *
     * @param status the driver status
     * @return list of drivers
     */
    List<Driver> findByStatus(Driver.DriverStatus status);

    /**
     * Find drivers with high acceptance rate.
     *
     * @param minAcceptanceRate the minimum acceptance rate
     * @return list of drivers
     */
    @Query("SELECT d FROM Driver d WHERE d.status = 'ACTIVE' AND d.acceptanceRate >= :minAcceptanceRate ORDER BY d.averageRating DESC")
    List<Driver> findHighRatedDrivers(@Param("minAcceptanceRate") java.math.BigDecimal minAcceptanceRate);

    /**
     * Find drivers by license verification status.
     *
     * @param licenseVerified the license verification status
     * @return list of drivers
     */
    List<Driver> findByLicenseVerified(Boolean licenseVerified);

    /**
     * Find drivers by background check status.
     *
     * @param backgroundCheckStatus the background check status
     * @return list of drivers
     */
    List<Driver> findByBackgroundCheckStatus(Driver.BackgroundCheckStatus backgroundCheckStatus);

    /**
     * Find drivers by vehicle inspection status.
     *
     * @param vehicleInspectionStatus the vehicle inspection status
     * @return list of drivers
     */
    List<Driver> findByVehicleInspectionStatus(Driver.VehicleInspectionStatus vehicleInspectionStatus);

    /**
     * Find drivers in batch by list of IDs.
     *
     * @param driverIds the list of driver IDs
     * @return list of drivers
     */
    List<Driver> findAllById(Iterable<UUID> driverIds);

    /**
     * Count active drivers.
     *
     * @return count of active drivers
     */
    @Query("SELECT COUNT(d) FROM Driver d WHERE d.status = 'ACTIVE'")
    long countActiveDrivers();

    /**
     * Count online drivers.
     *
     * @return count of online drivers
     */
    @Query("SELECT COUNT(d) FROM Driver d WHERE d.status = 'ACTIVE' AND d.availabilityStatus = 'ONLINE'")
    long countOnlineDrivers();
}
