package com.rideshare.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper configuration for consistent JSON serialization.
 * Configures ISO-8601 date/time formatting and proper Java Time support.
 */
@Configuration
public class ObjectMapperConfig {

    /**
     * Create a configured ObjectMapper bean.
     *
     * @return ObjectMapper with Java 21 time support
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Enable Java Time module for LocalDateTime, Instant, etc.
        mapper.registerModule(new JavaTimeModule());

        // Write dates as strings in ISO-8601 format
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
