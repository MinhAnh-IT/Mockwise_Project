package com.mockwise.interview.dto.response;

import com.mockwise.interview.enums.AnswerStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubmitAnswerOutput(
        UUID answerId,
        AnswerStatus status,
        OffsetDateTime submittedAt
) {}
