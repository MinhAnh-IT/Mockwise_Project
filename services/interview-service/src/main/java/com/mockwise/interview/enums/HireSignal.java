package com.mockwise.interview.enums;

/**
 * Mirrors the AI service's hire signal enum. Constant names use
 * {@code snake_case} so Jackson's default name strategy round-trips
 * the wire format ({@code "strong_yes"}) without a custom serializer.
 */
public enum HireSignal {
    strong_yes,
    yes,
    weak_yes,
    no,
    strong_no
}
