package com.mockwise.practice.dto.response;

import com.mockwise.practice.enums.SubmissionStatus;

/** 202 response to Run/Submit — the client then polls {@code GET /submissions/{id}}. */
public record SubmissionCreatedResponse(
        String submissionId,
        SubmissionStatus status
) {}
