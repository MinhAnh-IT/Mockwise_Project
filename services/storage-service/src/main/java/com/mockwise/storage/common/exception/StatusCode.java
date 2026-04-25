package com.mockwise.storage.common.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum StatusCode {

    // --- Object lifecycle ---
    STORAGE_OBJECT_NOT_FOUND(4040, "Storage object not found", 404),
    STORAGE_OBJECT_ALREADY_COMPLETED(4090, "Storage object is already marked as READY", 409),
    STORAGE_UPLOAD_NOT_FOUND_IN_BUCKET(4041, "Upload was not found in the bucket — client must PUT the file before completing", 404),
    STORAGE_UPLOAD_SIZE_MISMATCH(4220, "Uploaded size does not match declared size", 422),

    // --- Validation ---
    INVALID_CONTENT_TYPE(4001, "Content type %s is not allowed for kind %s", 400),
    UPLOAD_SIZE_EXCEEDED(4002, "Declared size %d exceeds the maximum allowed (%d bytes) for kind %s", 400),
    OWNERSHIP_VIOLATION(4030, "Caller is not the owner of this storage object", 403),

    // --- Internal auth ---
    INTERNAL_AUTH_FAILED(4011, "Missing or invalid internal API key", 401),

    // --- Infrastructure ---
    STORAGE_BACKEND_ERROR(5001, "Storage backend operation failed", 502);

    int code;
    String message;
    int httpStatus;

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}
