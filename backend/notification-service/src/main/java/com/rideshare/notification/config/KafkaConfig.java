package com.rideshare.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

/**
 * Kafka consumer configuration for event processing.
 * Configures automatic acknowledgment and error handling.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /**
     * Configure Kafka listener container factory with error handling.
     * Uses manual acknowledgment for reliable message processing.
     *
     * @return configured ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // Enable manual acknowledgment for guaranteed processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Configure error handling with backoff
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000, 3) // Retry 3 times with 1 second backoff
        ));

        // Set concurrency level based on available processors
        factory.setConcurrency(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

        return factory;
    }

    /**
     * Configure Kafka consumer factory with string deserialization.
     *
     * @return configured ConsumerFactory
     */
    @Bean
    public org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory(
            org.springframework.boot.autoconfigure.kafka.KafkaProperties kafkaProperties) {

        var consumerConfigs = kafkaProperties.buildConsumerProperties();
        consumerConfigs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerConfigs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(consumerConfigs);
    }
}
