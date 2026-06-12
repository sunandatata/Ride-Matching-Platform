package com.rideshare.eta.circuit;

/**
 * States for the Circuit Breaker pattern.
 * CLOSED: Normal operation
 * OPEN: API is failing, use fallback
 * HALF_OPEN: Testing if API recovered
 */
public enum CircuitBreakerState {
    CLOSED,      // Normal operation, forward requests to API
    OPEN,        // API failing, use fallback
    HALF_OPEN    // Testing recovery, allow limited requests
}
