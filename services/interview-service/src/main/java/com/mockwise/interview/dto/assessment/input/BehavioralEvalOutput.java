package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Completeness;

import java.util.List;

/**
 * Projection of the BehavioralOutput payload from
 * {@code AI/models/outputs.py}. Captures everything the report UI needs in
 * addition to what the verdict mapper consumes — STAR breakdown with
 * excerpts, signal evidence, red-flag details, and the feedback bundle.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} guarantees the AI
 * service can keep adding fields without a coordinated deploy here.
 *
 * <p>The AI service emits camelCase JSON (via Pydantic CamelModel base),
 * so the default Jackson camelCase binding works without per-DTO
 * overrides — same convention as everywhere else in the Mockwise stack.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BehavioralEvalOutput(
        int overallScore,
        Completeness completeness,
        Scores scores,
        StarBreakdown starBreakdown,
        List<SignalItem> signalCoverage,
        List<RedFlag> redFlags,
        Feedback feedback,
        Summary summary
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scores(
            ScoreItem starStructure,
            ScoreItem relevance,
            ScoreItem specificity,
            ScoreItem impactResult,
            ScoreItem selfAwareness
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StarBreakdown(
            StarComponent situation,
            StarComponent task,
            StarComponent action,
            StarComponent result
    ) {}

    /**
     * One of the four STAR components. {@code quality} comes from the AI
     * Quality enum ({@code excellent / good / acceptable / weak / missing})
     * — kept as a raw string here so future enum values don't break
     * deserialisation. {@code excerpt} is a direct quote from the
     * candidate's transcript and stays in their original language.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StarComponent(
            boolean detected,
            String quality,
            String excerpt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignalItem(
            String signalName,
            boolean detected,
            String evidence
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RedFlag(
            String type,
            String severity,
            String detail
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feedback(
            List<String> strengths,
            List<String> improvements,
            String sampleStrongerAnswerStructure
    ) {}
}
