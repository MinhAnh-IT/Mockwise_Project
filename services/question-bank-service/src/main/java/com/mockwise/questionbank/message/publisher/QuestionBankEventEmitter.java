package com.mockwise.questionbank.message.publisher;

import com.mockwise.questionbank.entity.BehavioralQuestion;
import com.mockwise.questionbank.entity.CoreQuestion;
import com.mockwise.questionbank.entity.Question;
import com.mockwise.questionbank.enums.QuestionType;
import com.mockwise.questionbank.message.event.QuestionBankEvent;
import com.mockwise.questionbank.message.event.QuestionBankEventType;
import com.mockwise.questionbank.repository.BehavioralQuestionRepository;
import com.mockwise.questionbank.repository.CoreQuestionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

/**
 * Builds QuestionBankEvent payloads from entities and fires them via Spring's
 * ApplicationEventPublisher inside the caller's @Transactional boundary.
 *
 * The {@link QuestionBankEventPublisher} listener picks them up after commit
 * and pushes to Kafka.
 *
 * Only BEHAVIORAL and CORE_CONCEPTUAL questions are emitted — LIVE_CODING is
 * not consumed by AI-Question-Selector.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionBankEventEmitter {

    ApplicationEventPublisher applicationEventPublisher;
    BehavioralQuestionRepository behavioralRepository;
    CoreQuestionRepository coreRepository;

    public void emitActivated(Question base) {
        QuestionBankEvent event = buildSnapshotEvent(base, QuestionBankEventType.QUESTION_ACTIVATED);
        if (event != null) applicationEventPublisher.publishEvent(event);
    }

    public void emitUpdated(Question base) {
        QuestionBankEvent event = buildSnapshotEvent(base, QuestionBankEventType.QUESTION_UPDATED);
        if (event != null) applicationEventPublisher.publishEvent(event);
    }

    /**
     * Deactivation events do not require a snapshot — the consumer just needs
     * the questionId to remove the row from its index.
     */
    public void emitDeactivated(Question base) {
        if (!isIndexable(base.getType())) return;

        QuestionBankEvent event = QuestionBankEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(QuestionBankEventType.QUESTION_DEACTIVATED)
                .occurredAt(OffsetDateTime.now())
                .questionId(base.getId())
                .questionType(base.getType())
                .build();
        applicationEventPublisher.publishEvent(event);
    }

    private QuestionBankEvent buildSnapshotEvent(Question base, QuestionBankEventType type) {
        if (!isIndexable(base.getType())) return null;

        QuestionBankEvent.Snapshot snapshot = switch (base.getType()) {
            case BEHAVIORAL -> behavioralSnapshot(base);
            case CORE_CONCEPTUAL -> coreSnapshot(base);
            case LIVE_CODING -> null;
        };

        if (snapshot == null) {
            log.warn("Cannot build snapshot for question id={} type={}; event {} skipped",
                    base.getId(), base.getType(), type);
            return null;
        }

        return QuestionBankEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(type)
                .occurredAt(OffsetDateTime.now())
                .questionId(base.getId())
                .questionType(base.getType())
                .snapshot(snapshot)
                .build();
    }

    private QuestionBankEvent.Snapshot behavioralSnapshot(Question base) {
        BehavioralQuestion bq = behavioralRepository.findById(base.getId()).orElse(null);
        if (bq == null) return null;
        return QuestionBankEvent.Snapshot.builder()
                .text(bq.getText())
                .difficulty(base.getDifficulty().name())
                .tags(Arrays.asList(base.getTags()))
                .competency(bq.getCompetency().name())
                .expectedSignals(Arrays.asList(bq.getExpectedSignals()))
                .build();
    }

    private QuestionBankEvent.Snapshot coreSnapshot(Question base) {
        CoreQuestion cq = coreRepository.findById(base.getId()).orElse(null);
        if (cq == null) return null;
        return QuestionBankEvent.Snapshot.builder()
                .text(cq.getText())
                .difficulty(base.getDifficulty().name())
                .tags(Arrays.asList(base.getTags()))
                .domain(cq.getDomain().name())
                .targetRoles(Arrays.asList(cq.getTargetRoles()))
                .keyConcepts(Arrays.asList(cq.getKeyConcepts()))
                .depthExpected(cq.getDepthExpected())
                .build();
    }

    private boolean isIndexable(QuestionType type) {
        return type == QuestionType.BEHAVIORAL || type == QuestionType.CORE_CONCEPTUAL;
    }
}
