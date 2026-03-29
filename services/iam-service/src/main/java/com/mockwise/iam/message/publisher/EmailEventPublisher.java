package com.mockwise.iam.message.publisher;

import com.mockwise.iam.message.constants.KafkaTopics;
import com.mockwise.iam.message.event.EmailEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(EmailEvent event) {
        log.info("Publishing email event: type={}, to={}", event.getType(), event.getTo());
        kafkaTemplate.send(KafkaTopics.EMAIL, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish email event: type={}, to={}, error={}",
                                event.getType(), event.getTo(), ex.getMessage(), ex);
                    } else {
                        log.info("Email event published successfully: type={}, to={}, topic={}, partition={}, offset={}",
                                event.getType(), event.getTo(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}