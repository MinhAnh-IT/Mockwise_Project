package com.mockwise.interview.assessment.enums;

/**
 * How deep the answer went relative to the question's expected depth.
 * Drives the planner's decision to follow up: a SURFACE answer to an
 * intermediate-depth question is a Case C trigger.
 */
public enum Depth {
    SURFACE,
    MODERATE,
    DEEP
}
