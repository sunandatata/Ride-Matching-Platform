package com.rideshare.eta.service;

import com.rideshare.eta.batch.BatchRequest;
import com.rideshare.eta.batch.BatchResult;
import com.rideshare.eta.batch.RoutingAPIBatchProcessor;
import com.rideshare.eta.cache.RouteCacheService;
import com.rideshare.eta.dto.ETARequest;
import com.rideshare.eta.dto.ETAResponse;
import com.rideshare.eta.dto.RouteData;
import com.rideshare.eta.util.DistanceEstimator;
import com.rideshare.eta.util.GridCellCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates ETA calculation with caching, batching, and fallback logic.
 *
 * Fallback logic:
 * 1. Check cache (1ms, ~80% hit rate)
 * 2. Batch request to routing API (50ms timeout)
 * 3. Fallback to distance estimate (distance_km / 40 * 60 minutes)
 *
 * Strict 50ms timeout for Matching Engine compliance.
 */
@Service
public class ETAService {
    private static final Logger logger = LoggerFactory.getLogger(ETAService.class);
    private static final long TIMEOUT_MS = 50;

    private final RouteCacheService cacheService;
    private final RoutingAPIBatchProcessor batchProcessor;

    public ETAService(
            RouteCacheService cacheService,
            RoutingAPIBatchProcessor batchProcessor) {

        this.cacheService = cacheService;
        this.batchProcessor = batchProcessor;
    }

    /**
     * Calculates ETA between two points.
     * Returns result within 50ms for Matching Engine.
     */
    public ETAResponse calculateETA(ETARequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Generate cache key with time-of-day and day-of-week buckets
            String cacheKey = GridCellCalculator.generateCacheKey(
                request.fromLat(), request.fromLng(),
                request.toLat(), request.toLng(),
                GridCellCalculator.getHourBucket(),
                GridCellCalculator.getDayOfWeekBucket()
            );

            // Step 1: Check cache (1ms)
            var cached = cacheService.get(cacheKey);
            if (cached.isPresent()) {
                RouteData data = cached.get();
                long elapsed = System.currentTimeMillis() - startTime;
                logger.debug("Cache hit for ETA calculation in {}ms", elapsed);
                return toResponse(data, ETAResponse.ETAStatus.CACHED);
            }

            // Step 2: Batch request to routing API (with 50ms timeout)
            BatchRequest batchReq = new BatchRequest(
                cacheKey,
                request.fromLat(), request.fromLng(),
                request.toLat(), request.toLng(),
                new CompletableFuture<>()
            );

            CompletableFuture<BatchResult> future = batchProcessor.addRequest(batchReq);

            try {
                long remainingTimeMs = Math.max(1, TIMEOUT_MS - (System.currentTimeMillis() - startTime));
                BatchResult result = future.get(remainingTimeMs, TimeUnit.MILLISECONDS);

                if (result.isSuccess()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.debug("API call succeeded for ETA calculation in {}ms", elapsed);
                    return toResponse(result.routeData(), ETAResponse.ETAStatus.LIVE);
                }

            } catch (Exception e) {
                logger.debug("Batch processing failed or timed out: {}", e.getMessage());
            }

            // Step 3: Fallback to distance estimate
            DistanceEstimator.ETAEstimate estimate = DistanceEstimator.estimate(
                request.fromLat(), request.fromLng(),
                request.toLat(), request.toLng()
            );

            long elapsed = System.currentTimeMillis() - startTime;
            logger.debug("Using distance estimate for ETA calculation in {}ms", elapsed);

            if (elapsed > TIMEOUT_MS) {
                logger.warn("ETA calculation exceeded 50ms timeout: {}ms", elapsed);
            }

            return new ETAResponse(
                estimate.etaMinutes(),
                estimate.distanceKm(),
                ETAResponse.ETAStatus.ESTIMATED
            );

        } catch (Exception e) {
            logger.error("Unexpected error in ETA calculation", e);

            // Final fallback: minimal distance estimate
            DistanceEstimator.ETAEstimate fallback = DistanceEstimator.estimate(
                request.fromLat(), request.fromLng(),
                request.toLat(), request.toLng()
            );

            return new ETAResponse(
                fallback.etaMinutes(),
                fallback.distanceKm(),
                ETAResponse.ETAStatus.ESTIMATED
            );
        }
    }

    /**
     * Converts internal RouteData to external ETAResponse.
     */
    private ETAResponse toResponse(RouteData data, ETAResponse.ETAStatus status) {
        return new ETAResponse(data.etaMinutes(), data.distanceKm(), status);
    }
}
