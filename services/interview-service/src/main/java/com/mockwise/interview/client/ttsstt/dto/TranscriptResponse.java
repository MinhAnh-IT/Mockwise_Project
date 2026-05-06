package com.mockwise.interview.client.ttsstt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Mirror of tts-stt-service's {@code TranscriptResponse}. Only the
 * fields the orchestrator consumes are spelled out — everything else
 * is ignored by Jackson so the upstream can keep adding fields.
 *
 * <p>The {@code text} field is what we need to embed in the
 * {@code evaluation-requested} payload as
 * {@code answer.transcript}. {@code words} and {@code durationMs}
 * may grow into per-word evaluations later.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptResponse(
        String id,
        String answerId,
        String storageObjectId,
        String text,
        Integer durationMs,
        Integer wordCount,
        String languageCode,
        List<Word> words
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Word(String text, Integer startMs, Integer endMs, Float confidence) {}
}
