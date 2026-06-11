package com.mockwise.practice.enums;

/**
 * A user's relationship to a problem in the practice catalog.
 * Phase 0 always reports {@code NONE}; Phase 2/3 derive ATTEMPTED/SOLVED from
 * the user's submission history.
 */
public enum ProblemStatus {
    NONE,
    ATTEMPTED,
    SOLVED
}
