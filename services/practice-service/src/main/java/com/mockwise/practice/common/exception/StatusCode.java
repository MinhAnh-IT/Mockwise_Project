package com.mockwise.practice.common.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum StatusCode {

    // --- Catalog ---
    PROBLEM_NOT_FOUND(4040, "Problem not found", 404),

    // --- Submission (Phase 1+) ---
    SUBMISSION_NOT_FOUND(4041, "Submission not found", 404),
    LANGUAGE_NOT_SUPPORTED(4001, "Language is not supported: %s", 400),
    CODE_TOO_LARGE(4002, "Source code exceeds the maximum allowed size", 400),
    INVALID_PAYLOAD(4003, "Request is missing required fields", 400),

    // --- Upstream ---
    QUESTION_BANK_UNAVAILABLE(5021, "Question bank is unavailable. Please try again.", 502),
    JUDGE_DISPATCH_FAILED(5022, "Failed to dispatch the submission to the judge. Please try again.", 502),
    USER_PROFILE_UNAVAILABLE(5023, "User profile service is unavailable. Please try again.", 502),

    // --- Auth ---
    UNAUTHENTICATED(4010, "Authentication is required", 401);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
