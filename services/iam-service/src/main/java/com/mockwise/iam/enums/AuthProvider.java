package com.mockwise.iam.enums;

/**
 * How an account authenticates. {@code LOCAL} = email/password (may also have a
 * social provider linked). {@code GOOGLE}/{@code GITHUB} = created via OAuth.
 *
 * <p>This field is informational (analytics / "signed up with Google" UI). The
 * authoritative check for whether password sign-in is allowed is
 * {@code hashPass != null}, NOT this enum — a LOCAL user who later links Google
 * keeps their password.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    GITHUB
}
