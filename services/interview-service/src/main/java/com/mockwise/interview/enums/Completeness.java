package com.mockwise.interview.enums;

/**
 * Mirrors the AI service's {@code Completeness} enum (NO_ANSWER /
 * INCOMPLETE / COMPLETE) byte-for-byte so JSON round-trips on the
 * {@code evaluation-completed} topic without a translation layer.
 */
public enum Completeness {
    NO_ANSWER,
    INCOMPLETE,
    COMPLETE
}
