package com.mockwise.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strongly-typed projection of the AI {@code overall_reviewer} output that
 * lives on {@code interview_session.metadata.overallReview}.
 *
 * <p>Replaces the previous {@code Map<String, Object>} surface so the FE
 * (and any downstream caller) has a discoverable schema instead of guessing
 * at keys. Fields mirror {@code AI/models/session_review.py
 * OverallReviewOutput} 1:1 — Jackson's camelCase binding does the rest.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} on both records so
 * a forward-compatible AI schema bump (extra fields) keeps deserialising.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OverallReviewView(
        String sessionId,
        Float overallScore,
        String grade,
        String hireSignal,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<TopicSummary> perTopicSummary,
        List<String> recommendations
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopicSummary(
            String topicKind,
            String topicValue,
            String status,
            String comment
    ) {}
}
