package com.mockwise.iam.common.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * IAM-specific business error codes not covered by the library's ResponseCode.
 * For standard codes (NOT_FOUND, UNAUTHORIZED, etc.) use ResponseCode from the library.
 */
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum StatusCode {

    // --- Auth / Account ---
    INVALID_PASSWORD_OR_EMAIL(4150, "Invalid email or password", 401),
    ACCOUNT_NOT_VERIFIED(4350, "Account not verified", 403),
    ACCOUNT_NOT_FOUND(4003, "Account not found", 404),
    USER_ALREADY_EXISTS(4002, "Email %s already exists", 409),
    CREATE_PROFILE_FAILED(4001, "Create profile failed", 500),
    ACCOUNT_BLOCKED(4351, "Account has been blocked", 403),
    CANNOT_BLOCK_SELF(4352, "You cannot block your own account", 400),
    CANNOT_BLOCK_ADMIN(4353, "Admin accounts cannot be blocked", 400),

    // --- OTP ---
    INVALID_OTP(4004, "Invalid OTP", 400),
    OTP_STILL_VALID(4005, "OTP still valid. Please wait before requesting a new one.", 429),
    PASSWORD_INCORRECT(4006, "Incorrect password", 400),
    TOO_MANY_REQUESTS(4290, "Too many requests, please try again later", 429),

    // --- Token ---
    INVALID_TOKEN(4888, "Invalid token", 401),
    GEN_TOKEN_FAILED(4800, "Generate token failed", 500),
    ALGORITHM_NOT_SUPPORTED(4801, "SHA-256 algorithm not found", 500);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
