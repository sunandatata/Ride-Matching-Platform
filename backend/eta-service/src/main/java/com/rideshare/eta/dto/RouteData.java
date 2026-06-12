package com.rideshare.eta.dto;

import java.time.Instant;

/**
 * Internal representation of cached route data.
 */
public record RouteData(
    Integer etaMinutes,
    Double distanceKm,
    Instant cachedAt,
    String source // "LIVE" or "ESTIMATED"
) {}
