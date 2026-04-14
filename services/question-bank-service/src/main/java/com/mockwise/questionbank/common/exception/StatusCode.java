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
    FOR_JUDGE_NOT_SUPPORTED(4004, "for-judge payload is only available for LIVE_CODING questions", 400),

    // --- Validation ---
    INVALID_QUESTION_TYPE(4005, "Invalid question type: %s", 400);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
