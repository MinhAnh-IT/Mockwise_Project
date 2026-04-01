package com.interview.judge.kafka;

import com.interview.judge.dto.SubmissionEvent;
import com.interview.judge.service.JudgeOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SubmissionConsumer {

    private final JudgeOrchestrator judgeOrchestrator;

    /**
     * Listens to the "code-submission" Kafka topic and hands off each event
     * to {@link JudgeOrchestrator} for processing.
     *
     * <p>Any uncaught exception is logged so the consumer does not crash;
     * the offset is committed and the message is not retried.
     */
    @KafkaListener(topics = "code-submission", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(SubmissionEvent event) {
        log.info("Received submission event: submissionId={}, language={}, testCases={}",
                event.getSubmissionId(), event.getLanguage(),
                event.getTestCases() != null ? event.getTestCases().size() : 0);

        try {
            judgeOrchestrator.handle(event);
        } catch (Exception e) {
            log.error("Unhandled error processing submission: submissionId={}",
                    event.getSubmissionId(), e);
        }
    }
}
