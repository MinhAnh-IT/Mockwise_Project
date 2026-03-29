package com.mockwise.iam.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken {

    @Id
    @UuidGenerator
    String id;

    @Column(nullable = false, unique = true)
    String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userId", nullable = false)
    User user;

    @Column(nullable = false)
    String tokenHash;

    @Column(nullable = false)
    Instant issuedAt;

    @Column(nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    boolean revoked = false;
}
