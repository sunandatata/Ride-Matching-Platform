package com.rideshare.location.repository;

import com.rideshare.location.model.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository for DriverStatus persistence.
 * Provides quick lookup of online/offline status.
 */
@Repository
public interface DriverStatusRepository extends JpaRepository<DriverStatus, String> {

    /**
     * Find driver status by driver ID.
     */
    Optional<DriverStatus> findByDriverId(String driverId);
}
