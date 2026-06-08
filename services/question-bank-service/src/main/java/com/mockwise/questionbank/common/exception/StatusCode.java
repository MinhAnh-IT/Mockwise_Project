package com.mockwise.questionbank.common.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum StatusCode {

    // --- Question ---
    QUESTION_NOT_FOUND(4040, "Question not found", 404),
    QUESTION_TYPE_MISMATCH(4001, "Question type does not match the requested operation", 400),
    AUDIO_NOT_SUPPORTED(4002, "Audio is only supported for BEHAVIORAL and CORE_CONCEPTUAL questions", 400),
    AUDIO_NOT_AVAILABLE(4003, "Audio is not available for this question yet", 404),
    AUDIO_GENERATION_FAILED(5024, "Audio generation failed — TTS service is unavailable. Please try again.", 502),
    FOR_JUDGE_NOT_SUPPORTED(4004, "for-judge payload is only available for LIVE_CODING questions", 400),

    // --- Validation ---
    INVALID_QUESTION_TYPE(4005, "Invalid question type: %s", 400),
    FILTER_TYPE_NOT_SUPPORTED(4006, "Filter only supports BEHAVIORAL or CORE_CONCEPTUAL types", 400),
    FILTER_MISSING_COMPETENCY(4007, "competency is required when type=BEHAVIORAL", 400),
    FILTER_MISSING_DOMAIN(4008, "domain is required when type=CORE_CONCEPTUAL", 400),

    // --- Follow-up ---
    FOLLOW_UP_PARENT_NOT_FOUND(4042, "Parent question not found for follow-up lookup", 404),

    // --- AI coding-question generation (server-side proxy) ---
    AI_LEETCODE_PREMIUM(4031, "LeetCode problem is premium and cannot be fetched", 403),
    AI_LEETCODE_NOT_FOUND(4044, "LeetCode problem not found for the given URL or slug", 404),
    AI_INVALID_INPUT(4009, "AI generation request is invalid: %s", 400),
    AI_GENERATION_FAILED(5021, "AI generation failed: %s", 502),
    AI_SERVICE_UNAVAILABLE(5022, "AI service is unavailable", 503),
    AI_KEY_MISCONFIGURED(5023, "AI service API key is missing or invalid", 502);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
