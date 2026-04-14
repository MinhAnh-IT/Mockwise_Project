package com.mockwise.questionbank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mockwise.questionbank.entity.FunctionMeta;
import com.mockwise.questionbank.entity.TestCase;
import com.mockwise.questionbank.enums.Competency;
import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.Domain;
import com.mockwise.questionbank.enums.QuestionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable snapshot of a question captured at the time of the API call.
 * Interview Service stores this object to preserve interview history
 * even if the original question is modified or deleted later.
 *
 * Type-specific fields that do not apply to the question type are null
 * and omitted from JSON serialization via field-level @JsonInclude(NON_NULL).
 * Shared fields (e.g. audioKey) are always present even when null so that
 * consumers can distinguish "not set" from "not applicable".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuestionSnapshotResponse {

    // ── Metadata ─────────────────────────────────────────────────────────────
    OffsetDateTime snapshotAt;

    // ── Base fields (shared across all 3 question types) ─────────────────────
    String id;
    QuestionType type;
    Difficulty difficulty;
    List<String> tags;

    // ── BEHAVIORAL + CORE_CONCEPTUAL: question text and audio ────────────────
    // text/audioKey are always serialized (even when null) so consumers can
    // distinguish "audio not set yet" (null) from "type has no audio" (field absent).
    // For LIVE_CODING, the mapper ignores both so they remain null here.
    String text;
    String audioKey;

    // ── BEHAVIORAL ───────────────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Competency competency;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> expectedSignals;

    // ── CORE_CONCEPTUAL ──────────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> targetRoles;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Domain domain;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> keyConcepts;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String depthExpected;

    // ── LIVE_CODING ──────────────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String title;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String description;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Integer timeLimitMinutes;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String optimalTimeComplexity;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String optimalSpaceComplexity;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    FunctionMeta functionMeta;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String starterCode;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<TestCase> testCases;
}
