package com.mockwise.interview.dto.request;

import com.mockwise.interview.enums.InterviewType;

/**
 * Input to {@code SessionService.start}. The userId is sourced from the
 * authenticated principal — the caller doesn't pass it. Override fields
 * are optional ways for the user to deviate from the profile defaults
 * for this one session (per question-selection-design.md §2 mục 3).
 *
 * @param interviewType   BEHAVIORAL / CORE / MIXED — required
 * @param topicFocus      optional bumped-importance topic (e.g. "DATABASE")
 * @param timeBudgetMinutesOverride  optional shortcut session
 */
public record StartSessionInput(
        InterviewType interviewType,
        String topicFocus,
        Integer timeBudgetMinutesOverride
) {}
