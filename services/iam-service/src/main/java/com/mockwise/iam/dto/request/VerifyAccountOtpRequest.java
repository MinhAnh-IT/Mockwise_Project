package com.mockwise.iam.dto.request;

public record VerifyAccountOtpRequest(
    String email,
    String otp
){ }
