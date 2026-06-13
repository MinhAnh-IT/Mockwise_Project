package com.mockwise.iam.entity;

import com.mockwise.iam.enums.AuthProvider;
import com.mockwise.iam.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    @Id
    @UuidGenerator
    String userId;

    @Column(nullable = false, unique = true)
    String email;

    /**
     * BCrypt password hash. {@code null} for accounts that only sign in via a
     * social provider (Google/GitHub) and have never set a password. Password
     * sign-in is gated on this being non-null.
     */
    @Column(nullable = true)
    String hashPass;

    /**
     * How the account was created / last linked. Informational only — see
     * {@link AuthProvider}. Defaults to {@code LOCAL}.
     */
    @Column(nullable = false)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    AuthProvider authProvider = AuthProvider.LOCAL;

    /** Subject/id from the social provider (Google {@code sub}, GitHub user id). Null for pure-local accounts. */
    @Column
    String providerId;

    /**
     * Whether the user has filled in the required profile (track/level/city/...).
     * Local registrations create the profile up-front so they are complete; a
     * fresh OAuth account starts {@code false} until it completes the profile form.
     * Existing rows default to {@code true} via the column definition.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    boolean profileCompleted = true;

    @Column(nullable = false)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    Role role = Role.User;

    @Column(nullable = false)
    @Builder.Default
    int tokenVersion = 1;

    @Column(nullable = false)
    @Builder.Default
    boolean isVerified = false;

    /**
     * Admin-controlled ban flag. When true the account cannot sign in and any
     * live session is killed (block bumps {@code tokenVersion} and revokes all
     * refresh tokens). Distinct from {@code isVerified}: a verified user can
     * still be blocked.
     */
    @Column(nullable = false)
    @Builder.Default
    boolean blocked = false;
}
