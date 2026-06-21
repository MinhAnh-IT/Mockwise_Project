package com.mockwise.userprofile.dto.response;

import com.mockwise.userprofile.entity.FeedbackCategory;
import com.mockwise.userprofile.entity.FeedbackStatus;

import java.time.Instant;

public record FeedbackResponse(
        String id,
        String userId,
        Integer rating,
        FeedbackCategory category,
        String content,
        String contactEmail,
        FeedbackStatus status,
        String adminNote,
        Instant createdAt,
        Instant reviewedAt
) { }
