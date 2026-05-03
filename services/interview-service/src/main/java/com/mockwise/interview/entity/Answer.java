package com.mockwise.interview.entity;

import com.mockwise.interview.entity.enums.AnswerStatus;
import com.mockwise.interview.entity.enums.AnswerType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One submitted answer. Pinned to a {@code session_question} (not the
 * bank question id) so a follow-up's answer is unambiguously distinct
 * from the parent's answer.
 *
 * <p>{@code rawEvaluation} keeps the AI service's full payload for audit;
 * {@code verdict} is the canonical {@code AssessmentVerdict} the planner
 * reads.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "answer")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Answer {

    @Id
    @UuidGenerator
    UUID id;

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(name = "session_question_id", nullable = false)
    UUID sessionQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    AnswerType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    AnswerStatus status;

    @Column(name = "storage_object_id")
    UUID storageObjectId;

    @Column(name = "transcript_id")
    UUID transcriptId;

    @Column(columnDefinition = "TEXT")
    String code;

    @Column(length = 20)
    String language;

    Float score;

    @Column(name = "max_score")
    Float maxScore;

    @Type(JsonBinaryType.class)
    @Column(name = "rubric_scores", columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> rubricScores = new HashMap<>();

    @Column(columnDefinition = "TEXT")
    String feedback;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> verdict = new HashMap<>();

    @Type(JsonBinaryType.class)
    @Column(name = "raw_evaluation", columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> rawEvaluation = new HashMap<>();

    @Column(name = "error_code", length = 40)
    String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    String errorMessage;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "scored_at")
    OffsetDateTime scoredAt;
}
