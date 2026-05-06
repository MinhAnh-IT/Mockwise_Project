package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Completeness;

import java.util.List;

/**
 * Lean projection of the LiveCodingOutput payload. The AI exposes far more
 * (complexity strings, optimization hints, sample solutions); the mapper
 * only needs isOptimal + the per-dimension scores + the issue list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveCodingEvalOutput(
        int overallScore,
        Completeness completeness,
        Scores scores,
        Analysis analysis,
        Summary summary
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scores(
            ScoreItem timeComplexity,
            ScoreItem spaceComplexity,
            ScoreItem codeQuality,
            ScoreItem problemSolving
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Analysis(
            boolean isOptimal,
            List<CodeIssue> codeIssues
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodeIssue(String type, String detail) {}
}
