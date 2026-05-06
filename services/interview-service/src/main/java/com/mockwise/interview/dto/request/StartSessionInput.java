package com.mockwise.interview.dto.request;

import com.mockwise.interview.enums.InterviewType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Input to {@code SessionService.start}. The userId is sourced from the
 * authenticated principal — the caller doesn't pass it. Override fields
 * are optional ways for the user to deviate from the profile defaults
 * for this one session (per question-selection-design.md §2 mục 3).
 *
 * @param interviewType   BEHAVIORAL / CORE / MIXED — required
 //* @param topicFocus      optional bumped-importance topic (e.g. "DATABASE")
 * @param timeBudgetMinutesOverride  optional shortcut session
 */
public record StartSessionInput(

        @NotNull(message = "interviewType is required")
        InterviewType interviewType,

//        @Size(max = 50, message = "topicFocus must be at most 50 characters")
//        String topicFocus,

        @Min(value = 1, message = "timeBudgetMinutesOverride must be >= 1")
        Integer timeBudgetMinutesOverride
) {}
