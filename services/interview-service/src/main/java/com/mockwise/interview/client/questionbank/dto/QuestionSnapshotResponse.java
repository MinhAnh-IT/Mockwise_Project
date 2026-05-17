package com.mockwise.interview.client.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mirror of question-bank's {@code QuestionSnapshotResponse} —
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} so question-bank can
 * keep adding fields without forcing a coordinated deploy.
 *
 * <p>Stored as the JSONB body of {@code session_question.snapshot}; populated
 * by {@code QuestionPicker} at pin time so the per-answer evaluator and the
 * end-of-session reviewer have the full question context even if question-bank
 * later mutates or deletes the source row.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionSnapshotResponse(
        OffsetDateTime snapshotAt,
        String id,
        String type,
        String difficulty,
        List<String> tags,
        String text,
        String audioKey,

        // BEHAVIORAL
        String competency,
        List<String> expectedSignals,

        // CORE_CONCEPTUAL
        List<String> targetRoles,
        String domain,
        List<String> keyConcepts,
        String depthExpected,

        // LIVE_CODING
        String title,
        String description,
        String constraints,
        String optimalTimeComplexity,
        String optimalSpaceComplexity,
        Map<String, Object> functionMeta,
        Map<String, Object> starterCode,
        List<Map<String, Object>> testCases
) {}
