package com.rideshare.ride.repository;

import com.rideshare.ride.entity.RideEvent;
import com.rideshare.ride.entity.RideEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for RideEvent entity.
 * Supports audit trail and event sourcing patterns.
 */
@Repository
public interface RideEventRepository extends JpaRepository<RideEvent, Long> {

    /**
     * Find all events for a specific ride in chronological order.
     *
     * @param rideId the ride ID
     * @param pageable pagination information
     * @return page of events for this ride
     */
    Page<RideEvent> findByRideIdOrderByCreatedAtAsc(String rideId, Pageable pageable);

    /**
     * Find all events for a specific ride (all results, not paginated).
     *
     * @param rideId the ride ID
     * @return list of all events for this ride
     */
    List<RideEvent> findByRideIdOrderByCreatedAtAsc(String rideId);

    /**
     * Find events of a specific type for a ride.
     *
     * @param rideId the ride ID
     * @param eventType the event type
     * @return list of matching events
     */
    List<RideEvent> findByRideIdAndEventType(String rideId, RideEventType eventType);

    /**
     * Find events initiated by a specific user.
     *
     * @param initiatorId the initiator ID
     * @param pageable pagination information
     * @return page of events initiated by this user
     */
    Page<RideEvent> findByInitiatorId(String initiatorId, Pageable pageable);
}
