package com.rideshare.notification.config;

import com.rideshare.notification.dto.ConnectionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for connection state persistence.
 * Uses JSON serialization for complex objects.
 */
@Configuration
public class RedisConfig {

    /**
     * Configure RedisTemplate for ConnectionState serialization.
     *
     * @param connectionFactory the Redis connection factory
     * @return configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, ConnectionState> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, ConnectionState> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key serialization
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Value serialization using Jackson
        Jackson2JsonRedisSerializer<ConnectionState> jackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(ConnectionState.class);
        jackson2JsonRedisSerializer.setObjectMapper(new ObjectMapper().findAndRegisterModules());

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);

        // Hash key/value serialization
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

}
