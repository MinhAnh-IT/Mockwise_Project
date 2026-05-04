package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the AI service's per-dimension score block. {@code max} and
 * {@code weight} are part of the wire payload but the mapper only reads
 * {@code score} (0–100).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreItem(int score) {}
