package com.rideshare.notification.service;

import com.rideshare.notification.dto.ConnectionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Manages WebSocket connection state persistence in Redis.
 * Enables session recovery across different notification service instances.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConnectionStateService {

    private static final String CONNECTION_KEY_PREFIX = "conn:";
    private static final String USER_CONNECTIONS_KEY = "user:conns:";
    private static final long CONNECTION_STATE_TTL_SECONDS = 1800; // 30 minutes

    private final RedisTemplate<String, ConnectionState> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${notification.instance.id:default-instance}")
    private String instanceId;

    /**
     * Save a new connection state to Redis.
     *
     * @param userId the user ID
     * @param connectionId the connection ID (session ID)
     * @return saved ConnectionState
     */
    public ConnectionState saveConnection(String userId, String connectionId) {
        ConnectionState state = ConnectionState.builder()
                .connectionId(connectionId)
                .userId(userId)
                .instanceId(instanceId)
                .activeRides(new HashSet<>())
                .connectedAt(Instant.now())
                .lastHeartbeat(Instant.now())
                .build();

        redisTemplate.opsForValue().set(
                CONNECTION_KEY_PREFIX + connectionId,
                state,
                CONNECTION_STATE_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        // Also track user connections
        stringRedisTemplate.opsForSet().add(USER_CONNECTIONS_KEY + userId, connectionId);
        stringRedisTemplate.expire(USER_CONNECTIONS_KEY + userId, CONNECTION_STATE_TTL_SECONDS, TimeUnit.SECONDS);

        log.debug("Saved connection state for user: {}, connection: {}", userId, connectionId);
        return state;
    }

    /**
     * Retrieve a connection state by connection ID.
     *
     * @param connectionId the connection ID
     * @return ConnectionState if found
     */
    public Optional<ConnectionState> getConnection(String connectionId) {
        ConnectionState state = redisTemplate.opsForValue().get(CONNECTION_KEY_PREFIX + connectionId);
        return Optional.ofNullable(state);
    }

    /**
     * Add a ride subscription to a connection.
     *
     * @param connectionId the connection ID
     * @param rideId the ride ID to subscribe to
     */
    public void addRideSubscription(String connectionId, String rideId) {
        ConnectionState state = redisTemplate.opsForValue().get(CONNECTION_KEY_PREFIX + connectionId);
        if (state != null) {
            state.getActiveRides().add(rideId);
            state.updateHeartbeat();
            redisTemplate.opsForValue().set(
                    CONNECTION_KEY_PREFIX + connectionId,
                    state,
                    CONNECTION_STATE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("Added ride subscription - connection: {}, ride: {}", connectionId, rideId);
        }
    }

    /**
     * Remove a ride subscription from a connection.
     *
     * @param connectionId the connection ID
     * @param rideId the ride ID to unsubscribe from
     */
    public void removeRideSubscription(String connectionId, String rideId) {
        ConnectionState state = redisTemplate.opsForValue().get(CONNECTION_KEY_PREFIX + connectionId);
        if (state != null) {
            state.getActiveRides().remove(rideId);
            state.updateHeartbeat();
            redisTemplate.opsForValue().set(
                    CONNECTION_KEY_PREFIX + connectionId,
                    state,
                    CONNECTION_STATE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("Removed ride subscription - connection: {}, ride: {}", connectionId, rideId);
        }
    }

    /**
     * Update heartbeat for a connection to prevent expiration.
     *
     * @param connectionId the connection ID
     */
    public void updateHeartbeat(String connectionId) {
        ConnectionState state = redisTemplate.opsForValue().get(CONNECTION_KEY_PREFIX + connectionId);
        if (state != null) {
            state.updateHeartbeat();
            redisTemplate.opsForValue().set(
                    CONNECTION_KEY_PREFIX + connectionId,
                    state,
                    CONNECTION_STATE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        }
    }

    /**
     * Delete a connection from Redis.
     *
     * @param connectionId the connection ID
     */
    public void deleteConnection(String connectionId) {
        ConnectionState state = redisTemplate.opsForValue().get(CONNECTION_KEY_PREFIX + connectionId);
        if (state != null) {
            redisTemplate.delete(CONNECTION_KEY_PREFIX + connectionId);
            stringRedisTemplate.opsForSet().remove(USER_CONNECTIONS_KEY + state.getUserId(), connectionId);
            log.debug("Deleted connection state: {}", connectionId);
        }
    }

    /**
     * Get all connections for a user.
     *
     * @param userId the user ID
     * @return Set of connection IDs for this user
     */
    public Set<String> getUserConnections(String userId) {
        Set<String> connectionIds = stringRedisTemplate.opsForSet().members(USER_CONNECTIONS_KEY + userId);
        return connectionIds != null ? connectionIds : new HashSet<>();
    }

    /**
     * Get active rides for a connection (used for recovery after reconnection).
     *
     * @param connectionId the connection ID
     * @return Set of active ride IDs
     */
    public Set<String> getActiveRides(String connectionId) {
        return getConnection(connectionId)
                .map(ConnectionState::getActiveRides)
                .orElse(new HashSet<>());
    }
}
