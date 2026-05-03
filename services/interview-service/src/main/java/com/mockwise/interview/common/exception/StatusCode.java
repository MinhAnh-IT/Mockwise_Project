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
    PLACEHOLDER(0, "placeholder", 500);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
