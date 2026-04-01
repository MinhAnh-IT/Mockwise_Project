package com.interview.judge.kafka;

import com.interview.judge.dto.JudgeResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class JudgeResultProducer {

    private static final String TOPIC = "submission-judged";

    private final KafkaTemplate<String, JudgeResultEvent> kafkaTemplate;

    /**
     * Publishes the final judge result to the "submission-judged" Kafka topic.
     *
     * @param event the result event to publish
     */
    public void publish(JudgeResultEvent event) {
        log.info("Publishing judge result: submissionId={}, verdict={}",
                event.getSubmissionId(), event.getVerdict());

        kafkaTemplate.send(TOPIC, event.getSubmissionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish judge result: submissionId={}",
                                event.getSubmissionId(), ex);
                    } else {
                        log.info("Judge result published: submissionId={}, partition={}, offset={}",
                                event.getSubmissionId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
