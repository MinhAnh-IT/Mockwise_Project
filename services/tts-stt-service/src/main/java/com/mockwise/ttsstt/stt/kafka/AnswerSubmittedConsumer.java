package com.mockwise.ttsstt.stt.kafka;

import com.mockwise.ttsstt.stt.kafka.event.AnswerSubmittedEvent;
import com.mockwise.ttsstt.stt.service.SttOrchestrator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnswerSubmittedConsumer {

    SttOrchestrator orchestrator;

    @KafkaListener(
            topics = "${tts-stt.kafka.topic-answer-submitted}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(AnswerSubmittedEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.OFFSET) long offset,
                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                          Acknowledgment ack) {

        if (event == null) {
            log.warn("Received null event — deserialization failed: topic={}, partition={}, offset={}",
                    topic, partition, offset);
            ack.acknowledge();
            return;
        }
        log.info("Received answer-submitted: answerId={} storageObjectId={} topic={} partition={} offset={}",
                event.getAnswerId(), event.getStorageObjectId(), topic, partition, offset);
        try {
            orchestrator.trigger(event, false);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("STT processing failed for answerId={}", event.getAnswerId(), ex);
            throw ex;
        }
    }
}
