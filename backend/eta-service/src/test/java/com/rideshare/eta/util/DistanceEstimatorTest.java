package com.rideshare.eta.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DistanceEstimator.
 * Tests Haversine distance calculation and ETA estimation.
 */
class DistanceEstimatorTest {

    private static final double TOLERANCE = 0.1;  // 0.1km tolerance

    @Test
    void should_calculate_distance_between_two_points() {
        // Given - NYC to nearby point (approximately 1km)
        double fromLat = 40.7128;
        double fromLng = -74.0060;
        double toLat = 40.7128;
        double toLng = -74.0021;

        // When
        double distance = DistanceEstimator.calculateDistance(
            fromLat, fromLng, toLat, toLng
        );

        // Then
        assertTrue(distance > 0);
        assertTrue(distance < 0.5);  // Should be less than 500m
    }

    @Test
    void should_return_zero_distance_for_same_point() {
        // Given
        double lat = 40.7128;
        double lng = -74.0060;

        // When
        double distance = DistanceEstimator.calculateDistance(lat, lng, lat, lng);

        // Then
        assertEquals(0.0, distance, TOLERANCE);
    }

    @Test
    void should_estimate_ETA_from_distance() {
        // Given - 10km distance, should be ~15 minutes at 40 km/h
        double distance = 10.0;

        // When
        Integer etaMinutes = DistanceEstimator.estimateETAMinutes(distance);

        // Then
        assertEquals(15, etaMinutes);
    }

    @Test
    void should_return_minimum_1_minute_ETA() {
        // Given - very short distance
        double distance = 0.1;

        // When
        Integer etaMinutes = DistanceEstimator.estimateETAMinutes(distance);

        // Then
        assertEquals(1, etaMinutes);
    }

    @Test
    void should_handle_zero_distance() {
        // Given
        double distance = 0.0;

        // When
        Integer etaMinutes = DistanceEstimator.estimateETAMinutes(distance);

        // Then
        assertEquals(1, etaMinutes);  // Minimum 1 minute
    }

    @Test
    void should_combine_distance_and_ETA_estimation() {
        // Given
        double fromLat = 40.7128;
        double fromLng = -74.0060;
        double toLat = 40.7528;
        double toLng = -73.9860;

        // When
        DistanceEstimator.ETAEstimate estimate = DistanceEstimator.estimate(
            fromLat, fromLng, toLat, toLng
        );

        // Then
        assertNotNull(estimate);
        assertTrue(estimate.distanceKm() > 0);
        assertTrue(estimate.etaMinutes() > 0);
    }

    @Test
    void should_use_40_kmh_average_speed() {
        // Given - 40km distance should be 60 minutes at 40 km/h
        double distance = 40.0;

        // When
        Integer etaMinutes = DistanceEstimator.estimateETAMinutes(distance);

        // Then
        assertEquals(60, etaMinutes);
    }

    @Test
    void should_estimate_5km_as_7_8_minutes() {
        // Given
        double distance = 5.0;

        // When
        Integer etaMinutes = DistanceEstimator.estimateETAMinutes(distance);

        // Then
        assertTrue(etaMinutes >= 7 && etaMinutes <= 8);
    }

    @Test
    void should_estimate_1km_as_1_2_minutes() {
        // Given
        double distance = 1.0;

        // When
        Integer etaMinutes = DistanceEstimator.estimateETAMinutes(distance);

        // Then
        assertTrue(etaMinutes >= 1 && etaMinutes <= 2);
    }

    @Test
    void should_use_haversine_for_great_circle_distance() {
        // Given - known distance between two cities (approximate)
        // NYC to Boston is approximately 350km
        double nycLat = 40.7128;
        double nycLng = -74.0060;
        double bostonLat = 42.3601;
        double bostonLng = -71.0589;

        // When
        double distance = DistanceEstimator.calculateDistance(
            nycLat, nycLng, bostonLat, bostonLng
        );

        // Then
        assertTrue(distance > 300 && distance < 400);
    }

    @Test
    void should_handle_equator_crossing() {
        // Given - points around equator
        double fromLat = -10.0;
        double fromLng = 0.0;
        double toLat = 10.0;
        double toLng = 0.0;

        // When
        double distance = DistanceEstimator.calculateDistance(
            fromLat, fromLng, toLat, toLng
        );

        // Then
        assertTrue(distance > 0);
    }

    @Test
    void should_handle_meridian_crossing() {
        // Given - points across prime meridian
        double fromLat = 0.0;
        double fromLng = -10.0;
        double toLat = 0.0;
        double toLng = 10.0;

        // When
        double distance = DistanceEstimator.calculateDistance(
            fromLat, fromLng, toLat, toLng
        );

        // Then
        assertTrue(distance > 0);
    }

    @Test
    void should_be_symmetric() {
        // Given
        double lat1 = 40.7128;
        double lng1 = -74.0060;
        double lat2 = 40.7580;
        double lng2 = -73.9855;

        // When
        double d1 = DistanceEstimator.calculateDistance(lat1, lng1, lat2, lng2);
        double d2 = DistanceEstimator.calculateDistance(lat2, lng2, lat1, lng1);

        // Then
        assertEquals(d1, d2, TOLERANCE);
    }

    @Test
    void should_handle_antipode_points() {
        // Given - opposite sides of earth
        double lat1 = 0.0;
        double lng1 = 0.0;
        double lat2 = 0.0;
        double lng2 = 180.0;

        // When
        double distance = DistanceEstimator.calculateDistance(lat1, lng1, lat2, lng2);

        // Then - should be approximately Earth's radius (6371 km)
        assertTrue(distance > 19000 && distance < 20200);
    }

    @Test
    void should_estimate_typical_urban_ride() {
        // Given - typical 3.2km ride in NYC
        double distance = 3.2;

        // When
        DistanceEstimator.ETAEstimate estimate = DistanceEstimator.estimate(
            40.7128, -74.0060, 40.7428, -74.0060
        );

        // Then
        assertTrue(estimate.distanceKm() > 0);
        assertTrue(estimate.etaMinutes() > 0);
        assertTrue(estimate.etaMinutes() < 10);  // Should be less than 10 minutes
    }
}
