package com.rideshare.location.service;

import com.rideshare.location.model.DriverStatus;
import com.rideshare.location.repository.DriverStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/**
 * Service for managing driver online/offline status.
 * Tracks status in both Redis cache (fast) and PostgreSQL (persistent).
 */
@Slf4j
@Service
public class DriverStatusService {

    private final DriverStatusRepository driverStatusRepository;
    private final RedisGeoService redisGeoService;

    public DriverStatusService(DriverStatusRepository driverStatusRepository,
                               RedisGeoService redisGeoService) {
        this.driverStatusRepository = driverStatusRepository;
        this.redisGeoService = redisGeoService;
    }

    /**
     * Mark driver as online.
     * Updates both Redis cache and database.
     *
     * @param driverId Driver identifier
     */
    @Transactional
    public void markOnline(String driverId) {
        try {
            // Update Redis cache
            redisGeoService.markDriverOnline(driverId);

            // Update or create database record
            DriverStatus status = driverStatusRepository.findByDriverId(driverId)
                .orElseGet(() -> DriverStatus.builder()
                    .driverId(driverId)
                    .isOnline(true)
                    .lastHeartbeat(Instant.now())
                    .build());

            status.setIsOnline(true);
            status.setLastLocationUpdate(Instant.now());
            status.setLastHeartbeat(Instant.now());
            status.setUpdatedAt(Instant.now());

            driverStatusRepository.save(status);

            log.debug("Marked driver {} as online", driverId);

        } catch (Exception e) {
            log.error("Failed to mark driver {} as online", driverId, e);
        }
    }

    /**
     * Mark driver as offline.
     * Updates both Redis and database.
     *
     * @param driverId Driver identifier
     */
    @Transactional
    public void markOffline(String driverId) {
        try {
            // Remove from Redis Geo
            redisGeoService.removeDriver(driverId);

            // Update database
            DriverStatus status = driverStatusRepository.findByDriverId(driverId)
                .orElseGet(() -> DriverStatus.builder()
                    .driverId(driverId)
                    .isOnline(false)
                    .build());

            status.setIsOnline(false);
            status.setUpdatedAt(Instant.now());

            driverStatusRepository.save(status);

            log.info("Marked driver {} as offline", driverId);

        } catch (Exception e) {
            log.error("Failed to mark driver {} as offline", driverId, e);
        }
    }

    /**
     * Check if driver is currently online.
     * Checks Redis cache first, falls back to database.
     *
     * @param driverId Driver identifier
     * @return True if driver is online
     */
    public boolean isOnline(String driverId) {
        // Check Redis cache first (fast path)
        if (redisGeoService.isDriverOnlineCached(driverId)) {
            return true;
        }

        // Fall back to database
        try {
            return driverStatusRepository.findByDriverId(driverId)
                .map(DriverStatus::getIsOnline)
                .orElse(false);
        } catch (Exception e) {
            log.error("Failed to check online status for driver {}", driverId, e);
            return false;
        }
    }
}
