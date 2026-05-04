package com.mockwise.interview.assessment.enums;

/**
 * How well the candidate hit the expected signals/concepts of the question.
 * Distinct from a numeric score — captures the question "did they show the
 * thing we were testing for, regardless of overall polish?".
 */
public enum SignalStrength {
    NONE,
    PARTIAL,
    ADEQUATE,
    STRONG
}
