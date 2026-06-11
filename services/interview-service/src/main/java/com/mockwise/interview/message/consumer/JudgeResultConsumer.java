package com.mockwise.interview.message.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.message.event.SubmissionJudgedEvent;
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
 * Consumes {@code submission-judged} from judge-service — the verdict for a
 * CODE answer. Hands off to {@link AnswerService#applyCodeJudged}, which
 * applies the spam-guard (skip AI when the code never ran — CE/RE only) and
 * either records a cheap terminal verdict or forwards the run to the AI
 * {@code live_coding} evaluator.
 *
 * <p>Dedup keys on {@code submissionId} (= answer id) since judge-service's
 * event carries no event id; {@link AnswerService#applyCodeJudged} is
 * additionally idempotent on the answer status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JudgeResultConsumer {

    AnswerService answerService;
    EventDedupService dedup;
    ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${interview.kafka.topic.submission-judged:submission-judged}",
            groupId = "${spring.kafka.consumer.group-id:interview-service}",
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
            // practice-service shares this topic. Anything not INTERVIEW (null =
            // legacy interview) is not ours — ack and skip so we don't try to
            // resolve a non-existent answer and poison-loop the partition.
            if (event.origin() != null && !"INTERVIEW".equalsIgnoreCase(event.origin())) {
                log.debug("Skipping non-interview verdict submissionId={} origin={}",
                        event.submissionId(), event.origin());
                ack.acknowledge();
                return;
            }
            if (!dedup.markProcessed(event.submissionId(), "SUBMISSION_JUDGED")) {
                log.debug("submission-judged {} already processed — skipping", event.submissionId());
                ack.acknowledge();
                return;
            }
            log.info("submission-judged submissionId={} verdict={} cases={} offset={}",
                    event.submissionId(), event.verdict(),
                    event.results() != null ? event.results().size() : 0, offset);
            answerService.applyCodeJudged(
                    UUID.fromString(event.submissionId()),
                    event.verdict(),
                    event.results());
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to handle submission-judged at offset={}: {}", offset, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }
}
