package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Completeness;

import java.util.List;

/**
 * Lean projection of the BehavioralOutput payload from
 * {@code AI/models/outputs.py}. Captures only what the mapper consumes —
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} guarantees the AI
 * service can keep adding fields (feedback bullets, meta, etc.) without
 * a coordinated deploy here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BehavioralEvalOutput(
        int overallScore,
        Completeness completeness,
        Scores scores,
        List<SignalItem> signalCoverage,
        List<RedFlag> redFlags,
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
    public record SignalItem(String signalName, boolean detected) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RedFlag(String type, String severity) {}
}
