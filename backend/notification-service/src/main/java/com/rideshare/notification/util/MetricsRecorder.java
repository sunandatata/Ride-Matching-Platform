package com.rideshare.notification.util;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * Records metrics for WebSocket operations and message delivery.
 * Enables monitoring of connection count, message latency, and reconnections.
 */
@Component
@RequiredArgsConstructor
public class MetricsRecorder {

    private final MeterRegistry meterRegistry;

    /**
     * Record an active connection.
     *
     * @param count current number of active connections
     */
    public void recordActiveConnections(long count) {
        meterRegistry.gauge("websocket.connections.active", count);
    }

    /**
     * Record a connection opened event.
     */
    public void recordConnectionOpened() {
        meterRegistry.counter("websocket.connections.opened").increment();
    }

    /**
     * Record a connection closed event.
     */
    public void recordConnectionClosed() {
        meterRegistry.counter("websocket.connections.closed").increment();
    }

    /**
     * Record a reconnection event.
     */
    public void recordReconnection() {
        meterRegistry.counter("websocket.connections.reconnected").increment();
    }

    /**
     * Record message delivery latency.
     *
     * @param durationMs duration in milliseconds
     */
    public void recordMessageDeliveryLatency(long durationMs) {
        meterRegistry.timer("websocket.message.delivery.latency")
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Record a message delivered.
     *
     * @param messageType the type of message delivered
     */
    public void recordMessageDelivered(String messageType) {
        meterRegistry.counter("websocket.messages.delivered", "type", messageType).increment();
    }

    /**
     * Record a duplicate message detected.
     */
    public void recordDuplicateMessageDetected() {
        meterRegistry.counter("websocket.messages.duplicates").increment();
    }

    /**
     * Record a Kafka event received.
     *
     * @param eventType the type of event
     */
    public void recordEventReceived(String eventType) {
        meterRegistry.counter("kafka.events.received", "type", eventType).increment();
    }

    /**
     * Time a code block execution.
     *
     * @return Timer.Sample for stopping timer
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stop a timer and record the duration.
     *
     * @param sample the timer sample
     * @param timerName the timer name
     * @param tags optional tags
     */
    public void stopTimer(Timer.Sample sample, String timerName, String... tags) {
        if (tags.length > 0) {
            sample.stop(meterRegistry.timer(timerName, tags));
        } else {
            sample.stop(meterRegistry.timer(timerName));
        }
    }
}
