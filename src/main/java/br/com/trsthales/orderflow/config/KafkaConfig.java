package br.com.trsthales.orderflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.orders-created}")
    private String ordersCreatedTopic;

    @Bean
    public NewTopic ordersCreatedTopic() {
        return TopicBuilder.name(ordersCreatedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersCreatedDltTopic() {
        return TopicBuilder.name(ordersCreatedTopic + "-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            @Value("${app.kafka.retry.interval-ms}") long intervalMs,
            @Value("${app.kafka.retry.max-attempts}") long maxAttempts) {

        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);
        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervalMs, maxAttempts));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }
}