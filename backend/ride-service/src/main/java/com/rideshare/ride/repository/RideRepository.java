package com.rideshare.ride.repository;

import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.entity.RideStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Ride entity with sharding support.
 * Rides are sharded by ride_id hash % 8 to distribute load.
 * The sharding is handled at the service layer through ShardingRouter.
 */
@Repository
public interface RideRepository extends JpaRepository<Ride, String> {

    /**
     * Find all rides for a specific rider.
     *
     * @param riderId the rider ID
     * @param pageable pagination information
     * @return page of rides for this rider
     */
    Page<Ride> findByRiderId(String riderId, Pageable pageable);

    /**
     * Find all rides for a specific driver.
     *
     * @param driverId the driver ID
     * @param pageable pagination information
     * @return page of rides assigned to this driver
     */
    Page<Ride> findByDriverId(String driverId, Pageable pageable);

    /**
     * Find rides with a specific status.
     *
     * @param status the ride status
     * @param pageable pagination information
     * @return page of rides with given status
     */
    Page<Ride> findByStatus(RideStatus status, Pageable pageable);

    /**
     * Find matched rides that have not been accepted yet.
     * Used for matching timeout logic.
     *
     * @param status the status (MATCHED)
     * @param beforeTime rides matched before this time
     * @return list of expired matched rides
     */
    @Query("SELECT r FROM Ride r WHERE r.status = :status AND r.matchedAt < :beforeTime")
    List<Ride> findExpiredMatchedRides(@Param("status") RideStatus status, @Param("beforeTime") LocalDateTime beforeTime);

    /**
     * Find active rides for a specific driver.
     *
     * @param driverId the driver ID
     * @return list of active rides for the driver
     */
    @Query("SELECT r FROM Ride r WHERE r.driverId = :driverId AND r.status IN ('MATCHED', 'ACCEPTED', 'ARRIVED', 'STARTED')")
    List<Ride> findActiveRidesForDriver(@Param("driverId") String driverId);

    /**
     * Find rides created after a specific time.
     * Used for analytics and monitoring.
     *
     * @param startTime the start time
     * @param pageable pagination information
     * @return page of recent rides
     */
    Page<Ride> findByCreatedAtAfter(LocalDateTime startTime, Pageable pageable);

    /**
     * Count rides by status.
     * Used for operational metrics.
     *
     * @param status the ride status
     * @return count of rides with given status
     */
    long countByStatus(RideStatus status);

    /**
     * Count active rides for a specific driver.
     *
     * @param driverId the driver ID
     * @return count of active rides
     */
    @Query("SELECT COUNT(r) FROM Ride r WHERE r.driverId = :driverId AND r.status IN ('MATCHED', 'ACCEPTED', 'ARRIVED', 'STARTED')")
    long countActiveRidesForDriver(@Param("driverId") String driverId);
}
