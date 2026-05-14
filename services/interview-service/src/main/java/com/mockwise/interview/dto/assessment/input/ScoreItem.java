package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the AI service's per-dimension score block. {@code max} and
 * {@code weight} are part of the wire payload but never consumed here.
 * {@code note} is the 1-2 sentence rationale the AI attaches to every
 * dimension — surfaced to the candidate so a raw "specificity = 55" is
 * paired with "why".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreItem(int score, String note) {}
