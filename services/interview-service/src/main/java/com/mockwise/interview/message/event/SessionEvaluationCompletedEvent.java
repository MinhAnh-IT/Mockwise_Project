package com.mockwise.interview.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Mirror of the AI service's {@code SessionEvaluationCompletedEvent}
 * ({@code AI/models/session_events.py}). Carries the cross-question review
 * the {@code overall_reviewer} graph produced for a session that already
 * has every answer terminal.
 *
 * <p>{@code result} is parsed leniently into a {@code Map} so a future AI
 * schema change does not block consumption — the consumer only reads the
 * fields it understands and stashes the rest under
 * {@code session.metadata.overallReview}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionEvaluationCompletedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String sessionId,
        Map<String, Object> result
) {

    /**
     * Strongly-typed projection of the AI {@code OverallReviewOutput}.
     * Built on demand from {@link #result()} so the consumer can populate
     * the session row deterministically without losing forward-compat.
     */
    public record OverallReview(
            Float overallScore,
            String grade,
            String hireSignal,
            String summary,
            List<String> strengths,
            List<String> weaknesses,
            List<TopicSummary> perTopicSummary,
            List<String> recommendations
    ) {

        public record TopicSummary(
                String topicKind,
                String topicValue,
                String status,
                String comment
        ) {}
    }
}
