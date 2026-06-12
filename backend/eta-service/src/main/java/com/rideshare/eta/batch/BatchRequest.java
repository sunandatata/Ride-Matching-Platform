package com.rideshare.eta.batch;

import java.util.concurrent.CompletableFuture;

/**
 * A single ETA request in a batch.
 * Contains the route coordinates and a future to notify when result is available.
 */
public record BatchRequest(
    String cacheKey,
    Double fromLat,
    Double fromLng,
    Double toLat,
    Double toLng,
    CompletableFuture<BatchResult> future
) {}
