package com.mockwise.questionbank.message.publisher;

import com.mockwise.questionbank.message.constants.KafkaTopics;
import com.mockwise.questionbank.message.event.QuestionBankEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes QuestionBankEvent to Kafka only AFTER the originating
 * @Transactional method has committed.
 *
 * The service layer raises a Spring application event inside its transaction;
 * this listener is triggered after commit and pushes the payload to Kafka,
 * keyed by questionId so a single question's events stay in order on one
 * partition.
 *
 * If the JVM crashes between commit and Kafka send the event is lost — the
 * AI service's POST /admin/reindex endpoint is the documented recovery path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionBankEventPublisher {

    KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(QuestionBankEvent event) {
        log.info("Publishing question-bank event: type={} questionId={} eventId={}",
                event.getEventType(), event.getQuestionId(), event.getEventId());

        kafkaTemplate.send(KafkaTopics.QUESTION_BANK_EVENTS, event.getQuestionId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish question-bank event: type={} questionId={} error={}",
                                event.getEventType(), event.getQuestionId(), ex.getMessage(), ex);
                    } else {
                        log.debug("Published question-bank event: type={} questionId={} partition={} offset={}",
                                event.getEventType(), event.getQuestionId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
