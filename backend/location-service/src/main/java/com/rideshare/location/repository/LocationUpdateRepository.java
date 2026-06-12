package com.rideshare.location.repository;

import com.rideshare.location.model.LocationUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

/**
 * Repository for LocationUpdate persistence.
 * Provides data access abstraction for location audit trail.
 */
@Repository
public interface LocationUpdateRepository extends JpaRepository<LocationUpdate, Long> {

    /**
     * Find the most recent location for a driver.
     */
    LocationUpdate findFirstByDriverIdOrderByTimestampDesc(String driverId);

    /**
     * Find location history for a driver within a time range.
     * Used for replaying driver movement.
     */
    @Query("SELECT lu FROM LocationUpdate lu WHERE lu.driverId = :driverId AND lu.timestamp >= :startTime AND lu.timestamp <= :endTime ORDER BY lu.timestamp DESC")
    List<LocationUpdate> findHistoryByDriverIdAndTimeRange(
        @Param("driverId") String driverId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * Batch insert locations. Used by async batch processor.
     */
    List<LocationUpdate> saveAll(Iterable<LocationUpdate> entities);
}
