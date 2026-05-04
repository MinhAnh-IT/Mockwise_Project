package com.mockwise.interview.message.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.message.event.TranscriptFailedEvent;
import com.mockwise.interview.message.event.TranscriptReadyEvent;
import com.mockwise.interview.service.AnswerService;
import com.mockwise.interview.service.EventDedupService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code transcript-ready} and {@code transcript-failed} from
 * tts-stt-service. Two listeners share one component because they
 * touch the same {@code AnswerService} entry points.
 *
 * <p>Manual ack: every handler returns {@code ack.acknowledge()} only
 * after the business write has committed. A handler exception leaves
 * the offset uncommitted so the next poll redelivers — the
 * {@code processed_event} dedup makes that safe.
 *
 * <p>Manual JSON parsing: the consumer factory returns the raw String
 * payload; we parse here with {@link ObjectMapper} into the specific
 * event record. Trade-off vs. Spring's typed deserializer: more
 * boilerplate but no trusted-package config and no class-loader
 * surprises when the producer side is a Python service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TranscriptConsumer {

    AnswerService answerService;
    EventDedupService dedup;
    ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${interview.kafka.topic.transcript-ready:transcript-ready}",
            groupId = "${spring.kafka.consumer.group-id:interview-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTranscriptReady(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            TranscriptReadyEvent event = objectMapper.readValue(payload, TranscriptReadyEvent.class);
            if (event == null || event.answerId() == null) {
                log.warn("Bad transcript-ready payload at offset={}", offset);
                ack.acknowledge();
                return;
            }
            if (!dedup.markProcessed(event.eventId(), event.eventType())) {
                log.debug("transcript-ready {} already processed — skipping", event.eventId());
                ack.acknowledge();
                return;
            }
            log.info("transcript-ready answerId={} transcriptId={} offset={}",
                    event.answerId(), event.transcriptId(), offset);
            answerService.applyTranscriptReady(
                    UUID.fromString(event.answerId()),
                    UUID.fromString(event.transcriptId()),
                    event.languageCode());
            ack.acknowledge();
        } catch (Exception ex) {
            // Don't ack — let the next poll redeliver. processed_event
            // guards against double-handling once the underlying issue is
            // fixed (e.g. tts-stt internal endpoint comes back).
            log.error("Failed to handle transcript-ready at offset={}: {}", offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @KafkaListener(
            topics = "${interview.kafka.topic.transcript-failed:transcript-failed}",
            groupId = "${spring.kafka.consumer.group-id:interview-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTranscriptFailed(
            String payload,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            TranscriptFailedEvent event = objectMapper.readValue(payload, TranscriptFailedEvent.class);
            if (event == null || event.answerId() == null) {
                log.warn("Bad transcript-failed payload at offset={}", offset);
                ack.acknowledge();
                return;
            }
            if (!dedup.markProcessed(event.eventId(), event.eventType())) {
                ack.acknowledge();
                return;
            }
            log.info("transcript-failed answerId={} reason={} offset={}",
                    event.answerId(), event.errorCode(), offset);
            answerService.applyTranscriptFailed(
                    UUID.fromString(event.answerId()),
                    event.errorCode(),
                    event.errorMessage());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle transcript-failed at offset={}: {}", offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
