package com.rideshare.notification.config;

import com.rideshare.notification.service.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for graceful shutdown handling.
 * Ensures all WebSocket connections are properly drained before JVM termination.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class ShutdownConfig {

    private final NotificationService notificationService;

    /**
     * Create a shutdown hook for graceful connection draining.
     *
     * @return shutdown hook runnable
     */
    @Bean
    public java.lang.Runnable shutdownHook() {
        return () -> {
            log.info("Shutdown hook triggered - initiating graceful shutdown");
            notificationService.gracefulShutdown();
            log.info("Graceful shutdown completed");
        };
    }

    /**
     * Register the shutdown hook with the JVM.
     *
     * @param shutdownHook the shutdown hook
     * @return cleanup runner
     */
    @Bean
    public Object registerShutdownHook(java.lang.Runnable shutdownHook) {
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownHook, "notification-shutdown"));
        return new Object();
    }
}
