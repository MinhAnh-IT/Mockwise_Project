package com.mockwise.questionbank.message.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mockwise.questionbank.enums.QuestionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cross-service event published whenever a BEHAVIORAL or CORE_CONCEPTUAL
 * question's lifecycle state or embedding-relevant fields change.
 *
 * Consumers (currently only AI-Question-Selector) use this to keep their
 * vector index in sync with the question bank without having to call back.
 *
 * Payload carries a full snapshot so consumers do not need a follow-up HTTP
 * fetch and cannot race with subsequent edits.
 *
 * LIVE_CODING questions are NOT emitted on this topic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionBankEvent {

    String eventId;
    QuestionBankEventType eventType;
    OffsetDateTime occurredAt;

    String questionId;
    QuestionType questionType;

    /** Full snapshot — null for QUESTION_DEACTIVATED. */
    Snapshot snapshot;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Snapshot {
        String text;
        String difficulty;
        List<String> tags;

        // Behavioral fields
        String competency;
        List<String> expectedSignals;

        // Core conceptual fields
        String domain;
        List<String> targetRoles;
        List<String> keyConcepts;
        String depthExpected;
    }
}
