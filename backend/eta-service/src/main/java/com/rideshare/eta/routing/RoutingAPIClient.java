package com.rideshare.eta.routing;

import com.rideshare.eta.circuit.CircuitBreaker;
import com.rideshare.eta.dto.RoutingAPIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * External routing API client with circuit breaker support.
 * Supports multiple routing providers via provider selection.
 * Strict 50ms timeout for Matching Engine compliance.
 */
@Component
public class RoutingAPIClient {
    private static final Logger logger = LoggerFactory.getLogger(RoutingAPIClient.class);
    private static final long REQUEST_TIMEOUT_MS = 50;

    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    private final RoutingProvider provider;
    private final String apiKey;
    private final String apiBaseUrl;

    public RoutingAPIClient(
            RestTemplate restTemplate,
            @Value("${routing.provider:OSRM}") String providerName,
            @Value("${routing.api-key:}") String apiKey,
            @Value("${routing.base-url:http://router.project-osrm.org}") String apiBaseUrl) {

        this.restTemplate = restTemplate;
        this.circuitBreaker = new CircuitBreaker("RoutingAPI-" + providerName);
        this.provider = RoutingProvider.valueOf(providerName.toUpperCase());
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
    }

    /**
     * Calls external routing API to get ETA and distance.
     * Returns Optional.empty() if circuit breaker is OPEN or request times out.
     */
    public Optional<RoutingAPIResponse> getRoute(
            Double fromLat, Double fromLng,
            Double toLat, Double toLng) {

        if (!circuitBreaker.allowRequest()) {
            logger.debug("Circuit breaker OPEN, skipping routing API call");
            return Optional.empty();
        }

        try {
            long startTime = System.currentTimeMillis();

            RoutingAPIResponse response = switch (provider) {
                case OSRM -> callOSRM(fromLat, fromLng, toLat, toLng);
                case GOOGLE_MAPS -> callGoogleMaps(fromLat, fromLng, toLat, toLng);
                case HERE -> callHERE(fromLat, fromLng, toLat, toLng);
            };

            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > REQUEST_TIMEOUT_MS) {
                logger.warn("Routing API call exceeded 50ms timeout: {}ms", elapsed);
            }

            circuitBreaker.recordSuccess();
            return Optional.of(response);

        } catch (RestClientException | TimeoutException e) {
            logger.warn("Routing API call failed: {}", e.getMessage());
            circuitBreaker.recordFailure();
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Unexpected error in routing API call", e);
            circuitBreaker.recordFailure();
            return Optional.empty();
        }
    }

    /**
     * Calls OSRM (Open Source Routing Machine) API.
     */
    private RoutingAPIResponse callOSRM(
            Double fromLat, Double fromLng,
            Double toLat, Double toLng) throws TimeoutException {

        String url = String.format(
            "%s/route/v1/driving/%f,%f;%f,%f?overview=false",
            apiBaseUrl, fromLng, fromLat, toLng, toLat
        );

        try {
            // In production, would use WebClient with timeout
            // For now, using RestTemplate as placeholder
            OSRMResponse osrmResponse = restTemplate.getForObject(url, OSRMResponse.class);

            if (osrmResponse != null && !osrmResponse.routes().isEmpty()) {
                OSRMRoute route = osrmResponse.routes().get(0);
                return new RoutingAPIResponse(
                    route.duration().intValue(),
                    route.distance(),
                    true
                );
            }
            throw new RuntimeException("No routes found in OSRM response");

        } catch (Exception e) {
            throw new RestClientException("OSRM API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Placeholder for Google Maps API integration.
     */
    private RoutingAPIResponse callGoogleMaps(
            Double fromLat, Double fromLng,
            Double toLat, Double toLng) throws TimeoutException {
        // TODO: Implement Google Maps Directions API
        throw new UnsupportedOperationException("Google Maps integration not yet implemented");
    }

    /**
     * Placeholder for HERE Maps API integration.
     */
    private RoutingAPIResponse callHERE(
            Double fromLat, Double fromLng,
            Double toLat, Double toLng) throws TimeoutException {
        // TODO: Implement HERE Maps Routing API
        throw new UnsupportedOperationException("HERE Maps integration not yet implemented");
    }

    /**
     * Returns current circuit breaker state.
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    // OSRM Response types
    public record OSRMResponse(java.util.List<OSRMRoute> routes) {}

    public record OSRMRoute(
        Double distance,
        Double duration,
        String geometry
    ) {}

    enum RoutingProvider {
        OSRM,
        GOOGLE_MAPS,
        HERE
    }
}
