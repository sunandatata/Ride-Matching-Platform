package com.rideshare.eta.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rideshare.eta.dto.RouteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis-based route cache with 1-hour TTL.
 * Provides ~80% cache hit rate in urban areas.
 *
 * Cache key format: "route:{from_lat_cell}:{from_lng_cell}:{to_lat_cell}:{to_lng_cell}:{hour}:{day_of_week}"
 * TTL: 1 hour (3600 seconds)
 */
@Service
public class RouteCacheService {
    private static final Logger logger = LoggerFactory.getLogger(RouteCacheService.class);
    private static final long CACHE_TTL_SECONDS = 3600;  // 1 hour

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);

    public RouteCacheService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {

        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves route data from cache.
     */
    public Optional<RouteData> get(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                cacheHits.incrementAndGet();
                RouteData routeData = objectMapper.readValue(cached, RouteData.class);
                logger.debug("Cache hit for key: {}", cacheKey);
                return Optional.of(routeData);
            }

            cacheMisses.incrementAndGet();
            logger.debug("Cache miss for key: {}", cacheKey);
            return Optional.empty();

        } catch (Exception e) {
            logger.error("Error reading from cache for key {}: {}", cacheKey, e.getMessage());
            cacheMisses.incrementAndGet();
            return Optional.empty();
        }
    }

    /**
     * Stores route data in cache with TTL.
     */
    public void set(String cacheKey, RouteData routeData) {
        try {
            String serialized = objectMapper.writeValueAsString(routeData);
            redisTemplate.opsForValue().set(cacheKey, serialized, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            logger.debug("Cached route data for key: {}", cacheKey);

        } catch (Exception e) {
            logger.error("Error writing to cache for key {}: {}", cacheKey, e.getMessage());
        }
    }

    /**
     * Removes a key from cache.
     */
    public void delete(String cacheKey) {
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (deleted != null && deleted) {
                logger.debug("Deleted cache entry: {}", cacheKey);
            }
        } catch (Exception e) {
            logger.error("Error deleting cache entry {}: {}", cacheKey, e.getMessage());
        }
    }

    /**
     * Clears all cache entries (for testing).
     */
    public void clear() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            logger.info("Cleared all cache entries");
        } catch (Exception e) {
            logger.error("Error clearing cache: {}", e.getMessage());
        }
    }

    /**
     * Returns cache hit count.
     */
    public int getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Returns cache miss count.
     */
    public int getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Calculates cache hit rate as percentage.
     */
    public double getCacheHitRate() {
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        int total = hits + misses;

        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total * 100;
    }

    /**
     * Resets metrics (for testing).
     */
    public void resetMetrics() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }
}
