package com.mockwise.interview.dto.response;

import com.mockwise.interview.enums.InterviewType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StartSessionOutput(
        UUID sessionId,
        String targetRole,
        String level,
        InterviewType interviewType,
        int questionBudget,
        int timeBudgetMinutes,
        // The interview is bounded by this single session clock — the FE
        // renders one global countdown from startedAt → deadlineAt. There
        // is no per-question time limit.
        OffsetDateTime startedAt,
        OffsetDateTime deadlineAt,
        PinnedQuestionView firstQuestion
) {}
