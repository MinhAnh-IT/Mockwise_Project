package com.mockwise.practice.message.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.practice.message.event.SubmissionJudgedEvent;
import com.mockwise.practice.service.PracticeSubmissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code submission-judged}. interview-service and practice-service
 * share this topic with different group ids, so every verdict reaches both —
 * this listener fast-filters on {@code origin == PRACTICE} and ignores the
 * rest. Verdict application is idempotent (the service no-ops once a submission
 * is terminal), so an at-least-once redelivery is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubmissionJudgedConsumer {

    PracticeSubmissionService submissionService;
    ObjectMapper objectMapper;

    private static final String ORIGIN_PRACTICE = "PRACTICE";

    @KafkaListener(
            topics = "${practice.kafka.topic.submission-judged:submission-judged}",
            groupId = "${spring.kafka.consumer.group-id:practice-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSubmissionJudged(
            String payload,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            SubmissionJudgedEvent event = objectMapper.readValue(payload, SubmissionJudgedEvent.class);
            if (event == null || event.submissionId() == null) {
                log.warn("Bad submission-judged payload at offset={}", offset);
                ack.acknowledge();
                return;
            }
            // Not ours — interview owns it (or origin missing ⇒ legacy interview).
            if (!ORIGIN_PRACTICE.equalsIgnoreCase(event.origin())) {
                log.debug("Skipping non-practice verdict submissionId={} origin={}",
                        event.submissionId(), event.origin());
                ack.acknowledge();
                return;
            }
            log.info("submission-judged submissionId={} verdict={} cases={} offset={}",
                    event.submissionId(), event.verdict(),
                    event.results() != null ? event.results().size() : 0, offset);
            submissionService.applyJudged(event);
            ack.acknowledge();
        } catch (Exception ex) {
            // Don't ack — let the broker redeliver. applyJudged is idempotent.
            log.error("Failed to handle submission-judged at offset={}: {}", offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
