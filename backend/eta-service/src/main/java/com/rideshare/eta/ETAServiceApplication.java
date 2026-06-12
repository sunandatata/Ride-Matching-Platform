package com.rideshare.eta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.client.RestTemplate;

/**
 * ETA Service main application entry point.
 * Provides estimated times of arrival between geographic points.
 *
 * Features:
 * - Route caching with grid-based cells (1km × 1km)
 * - Time-of-day aware caching (peak vs off-peak)
 * - Request batching (100ms windows)
 * - External routing API integration (OSRM, Google Maps, HERE)
 * - Circuit breaker for API failures
 * - Fallback distance-based estimation
 * - Strict 50ms timeout for Matching Engine
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.rideshare.eta", "com.rideshare.shared"})
public class ETAServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ETAServiceApplication.class, args);
    }

    /**
     * Configures RestTemplate for external API calls.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Configures Redis template for cache operations.
     * Uses StringRedisSerializer for consistency with JSON serialization.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
