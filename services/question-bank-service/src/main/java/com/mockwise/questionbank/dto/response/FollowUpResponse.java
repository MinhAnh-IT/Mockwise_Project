package com.mockwise.questionbank.dto.response;

import java.util.List;

public record FollowUpResponse(
        String id,
        String parentQuestionId,
        String probesTargetKind,
        String probesTargetValue,
        String text,
        List<String> expectedPoints,
        String audioKey
) {}
