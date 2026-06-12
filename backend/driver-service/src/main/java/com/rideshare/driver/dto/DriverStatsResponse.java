package com.rideshare.driver.dto;

import java.math.BigDecimal;

/**
 * DTO for driver statistics response.
 * Contains performance metrics and ratings.
 */
public record DriverStatsResponse(
    String driverId,
    BigDecimal averageRating,
    Integer totalRides,
    BigDecimal acceptanceRate,
    BigDecimal cancellationRate,
    BigDecimal totalEarnings
) {}
