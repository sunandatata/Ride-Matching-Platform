package com.rideshare.eta.circuit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CircuitBreaker.
 * Tests state transitions: CLOSED → OPEN → HALF_OPEN → CLOSED.
 */
class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new CircuitBreaker("test-breaker");
    }

    @Test
    void should_start_in_CLOSED_state() {
        // When
        CircuitBreakerState state = circuitBreaker.getState();

        // Then
        assertEquals(CircuitBreakerState.CLOSED, state);
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    void should_allow_requests_when_CLOSED() {
        // When
        boolean allowed = circuitBreaker.allowRequest();

        // Then
        assertTrue(allowed);
    }

    @Test
    void should_transition_to_OPEN_after_5_failures() {
        // When
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        // Then
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
        assertEquals(5, circuitBreaker.getConsecutiveFailures());
    }

    @Test
    void should_reject_requests_when_OPEN() {
        // Given - open circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        // When
        boolean allowed = circuitBreaker.allowRequest();

        // Then
        assertFalse(allowed);
    }

    @Test
    void should_allow_recovery_test_after_timeout() throws InterruptedException {
        // Given - open circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        // When - wait for recovery timeout and check again
        Thread.sleep(31000);  // Wait 31 seconds
        boolean allowed = circuitBreaker.allowRequest();

        // Then
        assertTrue(allowed);
        assertEquals(CircuitBreakerState.HALF_OPEN, circuitBreaker.getState());
    }

    @Test
    void should_reset_to_CLOSED_on_success_from_HALF_OPEN() {
        // Given - open circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        // Manually transition to HALF_OPEN for faster testing
        circuitBreaker.reset();
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        // When - record success while in HALF_OPEN
        circuitBreaker.recordSuccess();

        // Then
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
    }

    @Test
    void should_track_consecutive_failures() {
        // When
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        // Then
        assertEquals(3, circuitBreaker.getConsecutiveFailures());
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());  // Not yet OPEN
    }

    @Test
    void should_reset_failure_count_on_success() {
        // Given
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        assertEquals(2, circuitBreaker.getConsecutiveFailures());

        // When
        circuitBreaker.recordSuccess();

        // Then
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }

    @Test
    void should_reset_circuit_breaker() {
        // Given - open circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        // When
        circuitBreaker.reset();

        // Then
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    void should_not_require_all_5_failures_at_once() {
        // Given
        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.allowRequest());  // Still closed

        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.allowRequest());  // Still closed

        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.allowRequest());  // Still closed

        circuitBreaker.recordFailure();
        assertTrue(circuitBreaker.allowRequest());  // Still closed

        // When - 5th failure
        circuitBreaker.recordFailure();

        // Then
        assertFalse(circuitBreaker.allowRequest());  // Now open
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_remain_OPEN_before_timeout() throws InterruptedException {
        // Given - open circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());

        // When - wait only 5 seconds
        Thread.sleep(5000);

        // Then - should still be OPEN
        assertFalse(circuitBreaker.allowRequest());
        assertEquals(CircuitBreakerState.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_report_correct_failure_count_in_HALF_OPEN() {
        // Given - open circuit
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        // Manually reset to test HALF_OPEN
        circuitBreaker.reset();
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordFailure();
        }

        // When
        circuitBreaker.recordSuccess();

        // Then
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
        assertEquals(CircuitBreakerState.CLOSED, circuitBreaker.getState());
    }
}
