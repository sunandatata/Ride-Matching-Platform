package com.rideshare.location.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for location.changed events.
 * Optimized for high-throughput publishing with batching.
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * ProducerFactory for String key/value pairs.
     * Batches events and uses compression for efficiency.
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Kafka broker connection
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // Key/value serialization
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Batching for throughput
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);  // 32KB
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);       // Wait up to 10ms

        // Compression for network efficiency
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Acks configuration - balance consistency vs latency
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");  // Leader ack only

        // Retries and timeouts
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate for sending events.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory) {

        return new KafkaTemplate<>(producerFactory);
    }
}
