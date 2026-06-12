package com.rideshare.eta.controller;

import com.rideshare.eta.dto.ETARequest;
import com.rideshare.eta.dto.ETAResponse;
import com.rideshare.eta.service.ETAService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for ETA calculation.
 * Thin routing layer: validates input, delegates to service, returns response.
 */
@RestController
@RequestMapping("/eta")
public class ETAController {

    private final ETAService etaService;

    public ETAController(ETAService etaService) {
        this.etaService = etaService;
    }

    /**
     * POST /eta/calculate
     * Calculate ETA between two geographic points.
     *
     * Request body:
     * {
     *   "from_lat": 40.7128,
     *   "from_lng": -74.0060,
     *   "to_lat": 40.7580,
     *   "to_lng": -73.9855
     * }
     *
     * Response:
     * {
     *   "eta_minutes": 12,
     *   "distance_km": 3.2,
     *   "status": "CACHED"
     * }
     *
     * @param request ETA request with coordinates
     * @return ETA response with duration and distance
     */
    @PostMapping("/calculate")
    public ResponseEntity<ETAResponse> calculateETA(@Valid @RequestBody ETARequest request) {
        ETAResponse response = etaService.calculateETA(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /eta/health
     * Health check endpoint for service monitoring.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ETA Service is healthy");
    }
}
