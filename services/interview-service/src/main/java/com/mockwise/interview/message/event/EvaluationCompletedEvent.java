package com.mockwise.interview.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Mirror of the AI service's {@code EvaluationCompletedEvent}
 * ({@code AI/models/evaluation_events.py}). The {@code result} field is
 * the verbatim final_output from the evaluator graph — one of
 * BehavioralOutput / ConceptualOutput / LiveCodingOutput in
 * {@code AI/models/outputs.py} — handed off as a {@code Map} here and
 * dispatched by question type inside {@code AnswerService}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationCompletedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String answerId,
        String sessionId,
        String questionId,
        String interviewType,
        Map<String, Object> result
) {}
