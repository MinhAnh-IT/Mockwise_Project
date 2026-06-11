package com.mockwise.practice.message.producer;

import com.mockwise.practice.message.event.CodeSubmissionEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Publishes a {@link CodeSubmissionEvent} to the {@code code-submission} topic
 * for judge-service to execute. The send is awaited (bounded) so a broker
 * failure surfaces synchronously — the caller then marks the submission FAILED
 * instead of leaving it stuck in JUDGING with no verdict ever coming.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CodeSubmissionProducer {

    KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${practice.kafka.topic.code-submission:code-submission}")
    String topic;

    /**
     * @throws Exception if the record is not acknowledged within the timeout —
     *         the orchestrator translates this into JUDGE_DISPATCH_FAILED.
     */
    public void publish(CodeSubmissionEvent event) throws Exception {
        log.info("Publishing code-submission: submissionId={} language={} cases={}",
                event.submissionId(), event.language(),
                event.testCases() != null ? event.testCases().size() : 0);
        kafkaTemplate.send(topic, event.submissionId(), event).get(30, TimeUnit.SECONDS);
    }
}
