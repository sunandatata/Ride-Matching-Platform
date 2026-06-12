package com.rideshare.eta.util;

/**
 * Fallback distance-based ETA estimation.
 * Uses Haversine formula to calculate great-circle distance between points.
 * Assumes average speed of 40 km/h for urban ridesharing.
 */
public class DistanceEstimator {
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double AVERAGE_SPEED_KMH = 40.0;  // Urban average speed
    private static final double TO_RADIANS = Math.PI / 180.0;

    /**
     * Calculates distance using Haversine formula.
     * Returns distance in kilometers.
     */
    public static double calculateDistance(
            Double fromLat, Double fromLng,
            Double toLat, Double toLng) {

        double lat1Rad = fromLat * TO_RADIANS;
        double lat2Rad = toLat * TO_RADIANS;
        double deltaLat = (toLat - fromLat) * TO_RADIANS;
        double deltaLng = (toLng - fromLng) * TO_RADIANS;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Estimates ETA in minutes based on distance and average speed.
     * Assumes 40 km/h average speed in urban areas.
     */
    public static Integer estimateETAMinutes(Double distanceKm) {
        double hours = distanceKm / AVERAGE_SPEED_KMH;
        return Math.max(1, (int) Math.round(hours * 60));  // Minimum 1 minute
    }

    /**
     * Combined method: calculates distance and estimates ETA.
     */
    public static ETAEstimate estimate(
            Double fromLat, Double fromLng,
            Double toLat, Double toLng) {

        double distance = calculateDistance(fromLat, fromLng, toLat, toLng);
        int eta = estimateETAMinutes(distance);
        return new ETAEstimate(eta, distance);
    }

    /**
     * Internal record for estimation result.
     */
    public record ETAEstimate(Integer etaMinutes, Double distanceKm) {}
}
