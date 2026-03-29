package com.mockwise.userprofile.common.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * User-Profile domain-specific error codes not covered by the library's ResponseCode.
 * For standard codes (NOT_FOUND, FORBIDDEN, etc.) use ResponseCode from the library.
 */
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum StatusCode {

    // --- Position ---
    POSITION_NOT_FOUND(5001, "Position with code %s not found", 404),
    POSITION_NOT_ACTIVE(5002, "Position with code %s is not active", 400),
    POSITION_ALREADY_EXISTS(5003, "Position with code %s already exists", 409),
    POSITION_ID_NOT_FOUND(5004, "Position with id %s not found", 404),
    POSITION_DELETE_FAILED(5005, "Cannot delete position with id %s", 409),
    POSITION_UPDATE_FAILED(5006, "Cannot update position with id %s", 400),

    // --- Track ---
    TRACK_NOT_FOUND(5010, "Position track with id %s not found", 404),
    TRACK_NOT_ACTIVE(5011, "Position track %s is not active", 400),
    TRACK_ALREADY_EXISTS(5012, "Position track with name %s already exists", 409),

    // --- Level ---
    LEVEL_NOT_FOUND(5020, "Position level with id %s not found", 404),
    LEVEL_NOT_ACTIVE(5021, "Position level %s is not active", 400),
    LEVEL_ALREADY_EXISTS(5022, "Position level with role %s already exists", 409),

    // --- General operation ---
    OPERATION_NOT_SUPPORTED(5030, "Operation not supported: %s", 400);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
