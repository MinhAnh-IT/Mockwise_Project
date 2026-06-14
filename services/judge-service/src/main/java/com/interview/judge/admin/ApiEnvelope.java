package com.interview.judge.admin;

/**
 * Minimal response envelope matching the shape the frontend's {@code unwrap()}
 * helper expects ({@code { code, message, data }}). judge-service does not
 * depend on the shared {@code com.core.apiresponse} library the other services
 * use, so the admin endpoints carry this small local copy instead.
 */
public record ApiEnvelope<T>(int code, String message, T data) {

    public static <T> ApiEnvelope<T> success(T data) {
        return new ApiEnvelope<>(200, "success", data);
    }
}
