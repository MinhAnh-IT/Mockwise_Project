package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Completeness;

import java.util.List;

/**
 * Lean projection of the ConceptualOutput payload. concept_coverage's
 * {@code correct} is nullable in the AI schema (null = "not mentioned, so
 * correctness undecidable"), so we use {@link Boolean} instead of
 * {@code boolean} to preserve that.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConceptualEvalOutput(
        int overallScore,
        Completeness completeness,
        Scores scores,
        List<ConceptItem> conceptCoverage,
        List<Misconception> misconceptions,
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
            Boolean correct
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Misconception(String claim) {}
}
