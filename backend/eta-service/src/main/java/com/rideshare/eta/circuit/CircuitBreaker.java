package com.rideshare.eta.circuit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit Breaker for external routing API calls.
 * Prevents cascading failures by falling back to distance estimation.
 *
 * Configuration:
 * - Failure threshold: 5 consecutive failures
 * - Timeout per request: 50ms
 * - Recovery timeout: 30 seconds
 */
public class CircuitBreaker {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private static final int FAILURE_THRESHOLD = 5;
    private static final long RECOVERY_TIMEOUT_MS = 30_000;  // 30 seconds

    private final AtomicReference<CircuitBreakerState> state =
        new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final String name;

    public CircuitBreaker(String name) {
        this.name = name;
    }

    /**
     * Records successful API call and resets failure count.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);

        if (state.get() == CircuitBreakerState.HALF_OPEN) {
            state.set(CircuitBreakerState.CLOSED);
            logger.info("Circuit breaker {} recovered to CLOSED state", name);
        }
    }

    /**
     * Records API failure and transitions state if threshold reached.
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        int failures = consecutiveFailures.incrementAndGet();

        if (failures >= FAILURE_THRESHOLD && state.get() == CircuitBreakerState.CLOSED) {
            state.set(CircuitBreakerState.OPEN);
            logger.warn("Circuit breaker {} opened after {} failures", name, failures);
        }
    }

    /**
     * Checks if circuit breaker allows request (and transitions state if needed).
     */
    public boolean allowRequest() {
        CircuitBreakerState currentState = state.get();

        if (currentState == CircuitBreakerState.CLOSED) {
            return true;
        }

        if (currentState == CircuitBreakerState.OPEN) {
            long timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get();
            if (timeSinceFailure >= RECOVERY_TIMEOUT_MS) {
                state.set(CircuitBreakerState.HALF_OPEN);
                consecutiveFailures.set(0);
                logger.info("Circuit breaker {} transitioning to HALF_OPEN", name);
                return true;
            }
            return false;
        }

        // HALF_OPEN state: allow the test request
        return true;
    }

    /**
     * Returns current state.
     */
    public CircuitBreakerState getState() {
        return state.get();
    }

    /**
     * Returns consecutive failure count.
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Resets circuit breaker to CLOSED state.
     */
    public void reset() {
        state.set(CircuitBreakerState.CLOSED);
        consecutiveFailures.set(0);
        lastFailureTime.set(0);
        logger.info("Circuit breaker {} reset to CLOSED", name);
    }
}
