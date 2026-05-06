package com.mockwise.interview.message.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.message.event.SessionEvaluationCompletedEvent;
import com.mockwise.interview.message.event.SessionEvaluationFailedEvent;
import com.mockwise.interview.service.EventDedupService;
import com.mockwise.interview.service.SessionService;
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
 * Consumes the cross-question review the AI {@code overall_reviewer} graph
 * publishes once a session reaches "every answer terminal". The completed
 * branch flips the session to SCORED and stages {@code interview-scored}
 * for mail-service; the failed branch records the error and leaves the
 * session COMPLETED for manual replay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionEvaluationConsumer {

    SessionService sessionService;
    EventDedupService dedup;
    ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${interview.kafka.topic.session-evaluation-completed:session-evaluation-completed}",
            groupId = "${spring.kafka.consumer.group-id:interview-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCompleted(
            String payload,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            SessionEvaluationCompletedEvent event =
                    objectMapper.readValue(payload, SessionEvaluationCompletedEvent.class);
            if (event == null || event.sessionId() == null) {
                log.warn("Bad session-evaluation-completed payload at offset={}", offset);
                ack.acknowledge();
                return;
            }
            if (!dedup.markProcessed(event.eventId(), event.eventType())) {
                log.debug("session-evaluation-completed {} already processed — skipping", event.eventId());
                ack.acknowledge();
                return;
            }
            log.info("session-evaluation-completed sessionId={} offset={}",
                    event.sessionId(), offset);
            sessionService.applyOverallReview(UUID.fromString(event.sessionId()), event.result());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle session-evaluation-completed at offset={}: {}",
                    offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @KafkaListener(
            topics = "${interview.kafka.topic.session-evaluation-failed:session-evaluation-failed}",
            groupId = "${spring.kafka.consumer.group-id:interview-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFailed(
            String payload,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            SessionEvaluationFailedEvent event =
                    objectMapper.readValue(payload, SessionEvaluationFailedEvent.class);
            if (event == null || event.sessionId() == null) {
                log.warn("Bad session-evaluation-failed payload at offset={}", offset);
                ack.acknowledge();
                return;
            }
            if (!dedup.markProcessed(event.eventId(), event.eventType())) {
                ack.acknowledge();
                return;
            }
            log.warn("session-evaluation-failed sessionId={} error={} offset={}",
                    event.sessionId(), event.error(), offset);
            sessionService.applyOverallReviewFailed(
                    UUID.fromString(event.sessionId()),
                    event.error(),
                    event.detail());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle session-evaluation-failed at offset={}: {}",
                    offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
