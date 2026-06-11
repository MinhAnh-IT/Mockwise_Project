package com.mockwise.practice.dto.response;

/**
 * Per-test-case result in a submission detail. {@code stdout}/{@code stderr}
 * are populated only for visible (sample) cases — for hidden cases they are
 * null so the expected I/O never leaks to the user.
 */
public record SubmissionCaseView(
        int orderIndex,
        String testCaseId,
        String status,
        Integer runtimeMs,
        Integer memoryKb,
        boolean hidden,
        String stdout,
        String stderr
) {}
