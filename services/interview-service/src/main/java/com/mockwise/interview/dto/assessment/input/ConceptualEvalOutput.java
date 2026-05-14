package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Completeness;

import java.util.List;

/**
 * Projection of the ConceptualOutput payload. {@code conceptCoverage}'s
 * {@code correct} is nullable in the AI schema (null = "not mentioned, so
 * correctness undecidable"), so we use {@link Boolean}.
 *
 * <p>Carries everything the report UI shows: per-concept quotes &
 * corrections, the level-calibration block, misconception explanations,
 * and the feedback bundle with prioritized study points.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConceptualEvalOutput(
        int overallScore,
        Completeness completeness,
        Scores scores,
        List<ConceptItem> conceptCoverage,
        LevelCalibration levelCalibration,
        List<Misconception> misconceptions,
        Feedback feedback,
        Summary summary
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scores(
            ScoreItem accuracy,
            ScoreItem depth,
            ScoreItem practicalApplication,
            ScoreItem clarity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConceptItem(
            String conceptName,
            boolean mentioned,
            Boolean correct,
            String candidateStatement,
            String correction
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LevelCalibration(
            String expectedLevel,
            String actualDemonstratedLevel,
            String gap
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Misconception(String claim, String correction) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feedback(
            List<String> strengths,
            List<String> improvements,
            List<String> keyPointsToStudy
    ) {}
}
