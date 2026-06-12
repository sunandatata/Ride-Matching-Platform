package com.rideshare.location.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Location service configuration.
 * Enables async processing and scheduled tasks for batching.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class LocationConfig {
}
