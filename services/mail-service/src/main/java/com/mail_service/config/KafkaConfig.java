package com.mail_service.config;

import com.mail_service.dto.EmailEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic emailTopic() {
        return TopicBuilder.name("email-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailEvent> emailKafkaListenerContainerFactory(
            ConsumerFactory<String, EmailEvent> consumerFactory) {

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Failed to process email event after retries: topic={}, offset={}, error={}",
                        record.topic(), record.offset(), exception.getMessage(), exception),
                new FixedBackOff(2000L, 3L)
        );

        ConcurrentKafkaListenerContainerFactory<String, EmailEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
