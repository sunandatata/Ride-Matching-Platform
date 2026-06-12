package com.rideshare.eta.batch;

import com.rideshare.eta.dto.RouteData;

/**
 * Result of a batched routing request.
 */
public record BatchResult(
    String cacheKey,
    RouteData routeData,
    String errorMessage
) {
    /**
     * Factory method for successful result.
     */
    public static BatchResult success(String cacheKey, RouteData routeData) {
        return new BatchResult(cacheKey, routeData, null);
    }

    /**
     * Factory method for failed result.
     */
    public static BatchResult failure(String cacheKey, String errorMessage) {
        return new BatchResult(cacheKey, null, errorMessage);
    }

    /**
     * Checks if result is successful.
     */
    public boolean isSuccess() {
        return errorMessage == null && routeData != null;
    }
}
