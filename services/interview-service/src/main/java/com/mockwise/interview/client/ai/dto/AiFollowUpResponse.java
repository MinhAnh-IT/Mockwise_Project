package com.mockwise.interview.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Mirror of the AI service's {@code FollowUpResponse}. The orchestrator
 * persists {@code questionText} + {@code expectedPoints} inline on the
 * {@code session_question} row when source = AI_GENERATED.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiFollowUpResponse(

        @JsonProperty("question_text")
        String questionText,

        @JsonProperty("expected_points")
        List<String> expectedPoints,

        String rationale,

        String source,

        @JsonProperty("model_meta")
        Map<String, Object> modelMeta
) {}
