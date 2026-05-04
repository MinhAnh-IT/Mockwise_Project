package com.mockwise.interview.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirror of the AI service's {@code FollowUpRequest} shape (snake_case JSON).
 * The AI side doesn't use our {@code ApiResponse} envelope — it returns the
 * structured payload directly.
 *
 * <p>Field-level {@code @JsonProperty} explicitly snake-cases the names
 * because the orchestrator's Spring Boot Jackson is camelCase by default.
 */
public record AiFollowUpRequest(

        @JsonProperty("session_id")
        String sessionId,

        @JsonProperty("parent_question")
        ParentQuestion parentQuestion,

        @JsonProperty("user_answer_transcript")
        String userAnswerTranscript,

        @JsonProperty("weak_target")
        WeakTarget weakTarget,

        @JsonProperty("strong_targets")
        List<StrongTarget> strongTargets,

        String difficulty,

        String language
) {

    public record ParentQuestion(
            String id,
            String type,
            String text,
            String competency,
            String domain,
            @JsonProperty("expected_signals") List<String> expectedSignals,
            @JsonProperty("key_concepts")     List<String> keyConcepts
    ) {}

    public record WeakTarget(String kind, String value, String severity) {}

    public record StrongTarget(String kind, String value) {}
}
