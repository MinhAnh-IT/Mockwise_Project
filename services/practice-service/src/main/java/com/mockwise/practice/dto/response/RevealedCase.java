package com.mockwise.practice.dto.response;

/**
 * A single failing hidden test case surfaced to the user after a SUBMIT does
 * not pass. Only one is revealed at a time (the first failing case), and only
 * on a non-accepted SUBMIT — enough for the user to reproduce and fix, without
 * dumping the whole hidden suite.
 *
 * <p>{@code input}/{@code expected} are JSON strings sourced from question-bank;
 * {@code actualOutput} is the user's own stdout for that case.
 */
public record RevealedCase(
        int orderIndex,
        String input,
        String expected,
        String actualOutput,
        String status
) {}
