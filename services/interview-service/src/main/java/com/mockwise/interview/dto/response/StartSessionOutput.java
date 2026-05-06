package com.mockwise.interview.dto.response;

import com.mockwise.interview.enums.InterviewType;

import java.util.UUID;

public record StartSessionOutput(
        UUID sessionId,
        String targetRole,
        String level,
        InterviewType interviewType,
        int questionBudget,
        int timeBudgetMinutes,
        PinnedQuestionView firstQuestion
) {}
