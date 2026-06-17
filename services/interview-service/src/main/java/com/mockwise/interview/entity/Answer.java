package com.mockwise.interview.entity;

import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;
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

    // Lever 2 (realtime-stt-plan.md §7). Which transcript scored this answer:
    // REALTIME_WEBSPEECH (fast path, browser-built) or BATCH_SCRIBE (legacy,
    // STT from the video). Null for CODE answers and pre-Lever-2 rows.
    @Column(name = "transcript_source", length = 32)
    String transcriptSource;

    // The transcript actually used to score (fast path). Persisted so the
    // report shows exactly what the AI graded, and so a follow-up can read the
    // parent's transcript without depending on the AI echoing it back.
    @Column(name = "realtime_transcript", columnDefinition = "TEXT")
    String realtimeTranscript;

    // The batch-STT transcript derived from the video, attached in the
    // background after upload finishes. Display/reconciliation only — does NOT
    // re-score by default (§8.1 #3).
    @Column(name = "authoritative_transcript", columnDefinition = "TEXT")
    String authoritativeTranscript;

    // Set true when the background batch transcript diverges materially from
    // the real-time one used to score (§8.1 #3). Null = not reconciled yet.
    @Column(name = "transcript_mismatch")
    Boolean transcriptMismatch;

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
