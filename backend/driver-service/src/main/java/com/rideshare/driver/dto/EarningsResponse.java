package com.rideshare.driver.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for driver earnings response.
 */
public record EarningsResponse(
    String driverId,
    BigDecimal totalEarnings,
    BigDecimal dailyEarnings,
    BigDecimal weeklyEarnings,
    BigDecimal monthlyEarnings,
    LocalDate periodStart,
    LocalDate periodEnd,
    Integer ridesCount
) {}
