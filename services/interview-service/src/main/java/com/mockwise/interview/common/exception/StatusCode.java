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

    PLACEHOLDER(0, "placeholder", 500);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
