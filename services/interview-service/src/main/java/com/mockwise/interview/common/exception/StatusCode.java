package com.mockwise.interview.common.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum StatusCode {

    // Codes will be added as endpoints are implemented. Reserved ranges:
    //   4001-4099: validation
    //   4040-4049: not found
    //   4090-4099: conflicts (state machine violations, duplicate submit)
    //   4291:      rate limit
    //   5001-5099: dependent service failures

    // ── Upstream service failures ────────────────────────────────────────────
    // Returned when a downstream call returns a parseable error envelope and
    // we want to surface the failure with our own code rather than proxying
    // the upstream code blindly.
    QUESTION_BANK_UNAVAILABLE(5001, "Question bank service is unavailable", 502),
    STORAGE_UNAVAILABLE(5002, "Storage service is unavailable", 502),
    USER_PROFILE_UNAVAILABLE(5003, "User-profile service is unavailable", 502),
    AI_SERVICE_UNAVAILABLE(5004, "AI service is unavailable", 502),
    UPSTREAM_RESPONSE_UNPARSABLE(5099, "Upstream service returned an unparsable response", 502),

    // ── Session lifecycle ────────────────────────────────────────────────────
    SESSION_NOT_FOUND(4041, "Interview session not found", 404),
    SESSION_NOT_OWNER(4031, "Caller is not the owner of this session", 403),
    SESSION_NOT_IN_PROGRESS(4090, "Session is not in IN_PROGRESS state", 409),
    SESSION_TIME_UP(4089, "Session time budget exhausted — the interview has ended", 409),
    SESSION_NOT_FINISHED(4091, "Session is not finished — cannot fetch report yet", 409),
    QUESTION_NOT_IN_SESSION(4092, "Question id is not part of this session", 400),
    BLUEPRINT_NOT_FOUND(4042, "No blueprint configured for the requested role/level/type", 404),

    // ── Answer state machine ────────────────────────────────────────────────
    ANSWER_NOT_FOUND(4043, "Answer not found", 404),
    ANSWER_OWNER_MISMATCH(4032, "Answer does not belong to the caller", 403),
    STORAGE_OBJECT_OWNER_MISMATCH(4033, "Storage object owner mismatch", 403),
    STORAGE_OBJECT_NOT_READY(4093, "Storage object is not READY", 409),
    STORAGE_OBJECT_WRONG_KIND(4094, "Storage object kind does not match the expected kind", 400),
    STORAGE_OBJECT_ALREADY_USED(4095, "Storage object is already used by another answer", 409),

    // ── Blueprint admin ──────────────────────────────────────────────────────
    BLUEPRINT_NO_TOPICS(4002, "Blueprint must have at least one topic", 400),
    BLUEPRINT_TOPIC_INVALID(4003, "Invalid topic for this interview type: %s", 400),
    BLUEPRINT_DUPLICATE(4096, "Another default blueprint already exists for this (role, level, type)", 409),
    BLUEPRINT_IN_USE(4097, "Cannot delete blueprint — sessions still reference it", 409),

    // ── Session finalization ────────────────────────────────────────────────
    SESSION_NOT_COMPLETED(4098, "Session is not COMPLETED — cannot finalize", 409),
    SESSION_REVIEW_FAILED(5005, "Overall review failed in AI service", 502),

    // ── Live coding ──────────────────────────────────────────────────────────
    QUESTION_NOT_CODING(4099, "Question is not a LIVE_CODING question", 409),

    // ── Validation ───────────────────────────────────────────────────────────
    VALIDATION_ERROR(4001, "Validation failed: %s", 400);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
