package com.mockwise.interview.dto.response;

import com.mockwise.interview.enums.InterviewType;

/**
 * Read-only projection of what a session WOULD look like for the caller,
 * resolved from the same blueprint {@code SessionService#start} would pick
 * (by the profile's track + level). Powers the practice intro screen so the
 * "time / question count" it shows are the real numbers for this user instead
 * of hard-coded guesses.
 *
 * <ul>
 *   <li>{@code timeBudgetMinutes} — the whole-session clock. The interview has
 *       no per-question limit; this is the hard cap, i.e. the maximum length,
 *       not an "expected" duration.</li>
 *   <li>{@code minQuestions} / {@code maxQuestions} — a RANGE, because the
 *       adaptive planner adds follow-ups on top of the base questions. For
 *       BEHAVIORAL/CORE the floor is one question per blueprint topic (no
 *       follow-ups) and the ceiling is the full question budget (every
 *       follow-up used), so the FE shows "min–max câu" ({@code adaptive =
 *       true}). For CODING the plan is fixed, so {@code minQuestions ==
 *       maxQuestions} and {@code adaptive = false}.</li>
 * </ul>
 */
public record SessionPreviewOutput(
        InterviewType interviewType,
        String targetRole,
        String level,
        int minQuestions,
        int maxQuestions,
        int timeBudgetMinutes,
        boolean adaptive
) {}
