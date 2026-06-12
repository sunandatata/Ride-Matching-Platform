package com.rideshare.notification.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Prevents duplicate messages from being delivered to WebSocket clients.
 * Uses Redis with TTL to track message IDs per ride.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MessageDeduplicator {

    private static final String DEDUP_KEY_PREFIX = "msg:dedup:";
    private static final long DEDUP_TTL_SECONDS = 3600; // 1 hour

    private final StringRedisTemplate redisTemplate;

    /**
     * Check if message has already been delivered for a ride.
     *
     * @param rideId the ride ID
     * @param messageId the unique message ID
     * @return true if message is new, false if already delivered
     */
    public boolean isNewMessage(String rideId, String messageId) {
        String key = DEDUP_KEY_PREFIX + rideId;

        // Try to add the message ID to the set
        Long addedCount = redisTemplate.opsForSet().add(key, messageId);

        if (addedCount != null && addedCount > 0) {
            // Set expiration on first insertion
            redisTemplate.expire(key, DEDUP_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        }

        return false;
    }

    /**
     * Clear deduplication state for a ride (called on ride completion).
     *
     * @param rideId the ride ID
     */
    public void clearRideState(String rideId) {
        String key = DEDUP_KEY_PREFIX + rideId;
        redisTemplate.delete(key);
        log.debug("Cleared deduplication state for ride: {}", rideId);
    }
}
