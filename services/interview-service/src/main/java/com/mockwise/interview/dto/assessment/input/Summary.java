package com.mockwise.interview.dto.assessment.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Grade;
import com.mockwise.interview.enums.HireSignal;

/**
 * The {@code summary} block at the bottom of every AI evaluator output.
 * one_line_verdict is dropped — the mapper does not consume it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Summary(
        Grade grade,
        HireSignal hireSignal
) {}
