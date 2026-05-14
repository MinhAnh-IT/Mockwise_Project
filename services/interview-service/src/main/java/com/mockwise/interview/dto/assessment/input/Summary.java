package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Grade;
import com.mockwise.interview.enums.HireSignal;

/**
 * The {@code summary} block at the bottom of every AI evaluator output.
 * {@code oneLineVerdict} is a single-sentence plain-language take that the
 * report UI shows next to the dimension scores.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Summary(
        Grade grade,
        HireSignal hireSignal,
        String oneLineVerdict
) {}
