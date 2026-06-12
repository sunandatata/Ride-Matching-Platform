package com.rideshare.eta.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GridCellCalculator.
 * Tests grid cell generation and cache key formatting.
 */
class GridCellCalculatorTest {

    @Test
    void should_calculate_latitude_cell() {
        // Given
        double lat = 40.7128;

        // When
        long cell = GridCellCalculator.latitudeToCell(lat);

        // Then
        assertEquals(4071, cell);
    }

    @Test
    void should_calculate_longitude_cell() {
        // Given
        double lng = -74.0060;

        // When
        long cell = GridCellCalculator.longitudeToCell(lng);

        // Then
        assertEquals(-7401, cell);
    }

    @Test
    void should_generate_cache_key() {
        // Given
        double fromLat = 40.7128;
        double fromLng = -74.0060;
        double toLat = 40.7580;
        double toLng = -73.9855;
        Integer hour = 14;
        Integer dayOfWeek = 2;

        // When
        String key = GridCellCalculator.generateCacheKey(
            fromLat, fromLng, toLat, toLng, hour, dayOfWeek
        );

        // Then
        assertTrue(key.startsWith("route:"));
        assertTrue(key.contains(":14:2"));
        assertEquals("route:4071:-7401:4076:-7399:14:2", key);
    }

    @Test
    void should_generate_consistent_cache_keys() {
        // Given
        double fromLat = 40.7128;
        double fromLng = -74.0060;
        double toLat = 40.7580;
        double toLng = -73.9855;

        // When
        String key1 = GridCellCalculator.generateCacheKey(
            fromLat, fromLng, toLat, toLng, 14, 2
        );
        String key2 = GridCellCalculator.generateCacheKey(
            fromLat, fromLng, toLat, toLng, 14, 2
        );

        // Then
        assertEquals(key1, key2);
    }

    @Test
    void should_generate_different_keys_for_different_times() {
        // Given
        double fromLat = 40.7128;
        double fromLng = -74.0060;
        double toLat = 40.7580;
        double toLng = -73.9855;

        // When
        String key1 = GridCellCalculator.generateCacheKey(
            fromLat, fromLng, toLat, toLng, 14, 2
        );
        String key2 = GridCellCalculator.generateCacheKey(
            fromLat, fromLng, toLat, toLng, 18, 2
        );

        // Then
        assertNotEquals(key1, key2);
    }

    @Test
    void should_generate_different_keys_for_different_days() {
        // Given
        double fromLat = 40.7128;
        double fromLng = -74.0060;
        double toLat = 40.7580;
        double toLng = -73.9855;

        // When
        String key1 = GridCellCalculator.generateCacheKey(
            fromLat, fromLng, toLat, toLng, 14, 2
        );
        String key2 = GridCellCalculator.generateCacheKey(
            fromLat, fromLng, toLat, toLng, 14, 5
        );

        // Then
        assertNotEquals(key1, key2);
    }

    @Test
    void should_handle_zero_coordinates() {
        // Given
        double zero = 0.0;

        // When
        long cell = GridCellCalculator.latitudeToCell(zero);

        // Then
        assertEquals(0, cell);
    }

    @Test
    void should_handle_negative_coordinates() {
        // Given
        double negLat = -33.8688;
        double negLng = 151.2093;

        // When
        long latCell = GridCellCalculator.latitudeToCell(negLat);
        long lngCell = GridCellCalculator.longitudeToCell(negLng);

        // Then
        assertEquals(-3387, latCell);
        assertEquals(15121, lngCell);
    }

    @Test
    void should_handle_boundary_coordinates() {
        // Given - max latitude
        double maxLat = 90.0;
        double minLat = -90.0;
        double maxLng = 180.0;
        double minLng = -180.0;

        // When
        long maxLatCell = GridCellCalculator.latitudeToCell(maxLat);
        long minLatCell = GridCellCalculator.latitudeToCell(minLat);
        long maxLngCell = GridCellCalculator.longitudeToCell(maxLng);
        long minLngCell = GridCellCalculator.longitudeToCell(minLng);

        // Then
        assertEquals(9000, maxLatCell);
        assertEquals(-9000, minLatCell);
        assertEquals(18000, maxLngCell);
        assertEquals(-18000, minLngCell);
    }

    @Test
    void should_grid_cells_enable_caching_nearby_points() {
        // Given - two nearby points within same 1km cell
        double lat1 = 40.7120;
        double lng1 = -74.0050;
        double lat2 = 40.7135;  // ~150m away
        double lng2 = -74.0065;

        // When
        long latCell1 = GridCellCalculator.latitudeToCell(lat1);
        long lngCell1 = GridCellCalculator.longitudeToCell(lng1);
        long latCell2 = GridCellCalculator.latitudeToCell(lat2);
        long lngCell2 = GridCellCalculator.longitudeToCell(lng2);

        // Then
        assertEquals(latCell1, latCell2);
        assertEquals(lngCell1, lngCell2);
    }

    @Test
    void should_grid_cells_differ_for_distant_points() {
        // Given - two distant points
        double lat1 = 40.7128;
        double lng1 = -74.0060;
        double lat2 = 40.7528;  // ~4.4km away
        double lng2 = -73.9860;

        // When
        long latCell1 = GridCellCalculator.latitudeToCell(lat1);
        long lngCell1 = GridCellCalculator.longitudeToCell(lng1);
        long latCell2 = GridCellCalculator.latitudeToCell(lat2);
        long lngCell2 = GridCellCalculator.longitudeToCell(lng2);

        // Then
        assertNotEquals(latCell1, latCell2);
        assertNotEquals(lngCell1, lngCell2);
    }
}
