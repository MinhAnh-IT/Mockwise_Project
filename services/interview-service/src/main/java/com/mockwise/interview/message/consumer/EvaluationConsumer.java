package com.mockwise.interview.message.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.message.event.EvaluationCompletedEvent;
import com.mockwise.interview.message.event.EvaluationFailedEvent;
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
 * Consumes {@code evaluation-completed} and {@code evaluation-failed}
 * from the AI service. The completed branch hands off to
 * {@link AnswerService#applyEvaluationCompleted} — the planner-driving
 * code path — and the failed branch flips the answer terminal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EvaluationConsumer {

    AnswerService answerService;
    EventDedupService dedup;
    ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${interview.kafka.topic.evaluation-completed:evaluation-completed}",
            groupId = "${spring.kafka.consumer.group-id:interview-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEvaluationCompleted(
            String payload,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            EvaluationCompletedEvent event = objectMapper.readValue(payload, EvaluationCompletedEvent.class);
            if (event == null || event.answerId() == null) {
                log.warn("Bad evaluation-completed payload at offset={}", offset);
                ack.acknowledge();
                return;
            }
            if (!dedup.markProcessed(event.eventId(), event.eventType())) {
                log.debug("evaluation-completed {} already processed — skipping", event.eventId());
                ack.acknowledge();
                return;
            }
            log.info("evaluation-completed answerId={} interviewType={} offset={}",
                    event.answerId(), event.interviewType(), offset);
            answerService.applyEvaluationCompleted(
                    UUID.fromString(event.answerId()),
                    event.result());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle evaluation-completed at offset={}: {}", offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @KafkaListener(
            topics = "${interview.kafka.topic.evaluation-failed:evaluation-failed}",
            groupId = "${spring.kafka.consumer.group-id:interview-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEvaluationFailed(
            String payload,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        try {
            EvaluationFailedEvent event = objectMapper.readValue(payload, EvaluationFailedEvent.class);
            if (event == null || event.answerId() == null) {
                log.warn("Bad evaluation-failed payload at offset={}", offset);
                ack.acknowledge();
                return;
            }
            if (!dedup.markProcessed(event.eventId(), event.eventType())) {
                ack.acknowledge();
                return;
            }
            log.info("evaluation-failed answerId={} reason={} offset={}",
                    event.answerId(), event.error(), offset);
            answerService.applyEvaluationFailed(
                    UUID.fromString(event.answerId()),
                    event.error(),
                    event.detail());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle evaluation-failed at offset={}: {}", offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
