package com.mail_service.message;

import com.mail_service.dto.EmailEvent;
import com.mail_service.service.impl.MailServiceImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailConsumer {

    MailServiceImpl mailService;

    @KafkaListener(
            topics = "email-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "emailKafkaListenerContainerFactory"
    )
    public void onMessage(
            EmailEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        if (event == null) {
            log.warn("Received null event — deserialization failed: topic={}, partition={}, offset={}",
                    topic, partition, offset);
            return;
        }
        log.info("Received email event: type={}, to={}, topic={}, partition={}, offset={}",
                event.getType(), event.getTo(), topic, partition, offset);
        mailService.sendHtml(event);
    }
}
