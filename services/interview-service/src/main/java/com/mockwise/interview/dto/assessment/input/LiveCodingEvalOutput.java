package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Completeness;

import java.util.List;

/**
 * Projection of the LiveCodingOutput payload. Carries the detected vs
 * optimal complexity strings, code-issue line numbers, the optimization
 * hint, and the optional sample optimal solution so the report can show
 * the candidate exactly what to fix and how.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveCodingEvalOutput(
        int overallScore,
        Completeness completeness,
        Scores scores,
        Analysis analysis,
        Feedback feedback,
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
            ComplexityInfo detectedComplexity,
            ComplexityInfo optimalComplexity,
            boolean isOptimal,
            List<CodeIssue> codeIssues
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComplexityInfo(String time, String space) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodeIssue(String type, Integer line, String detail) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feedback(
            List<String> strengths,
            List<String> improvements,
            String optimizationHint,
            String sampleOptimalSolution
    ) {}
}
