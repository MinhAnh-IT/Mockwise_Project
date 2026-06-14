package com.mockwise.iam.audit;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * One immutable admin audit record. Written only by {@link AuditConsumer}; never
 * updated. Schema is created by Hibernate ({@code ddl-auto: update}) — no Flyway
 * in this service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at"),
        @Index(name = "idx_audit_actor", columnList = "actor_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_target", columnList = "target_type, target_id")
})
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    @Column(name = "actor_id")
    String actorId;

    @Column(name = "actor_email")
    String actorEmail;

    @Column(name = "actor_role")
    String actorRole;

    /** High-level verb, e.g. {@code LOGIN}, {@code USER_BLOCK}, {@code QUESTION_UPDATE}. */
    @Column(nullable = false)
    String action;

    /** Coarse bucket for filtering: {@code AUTH}, {@code USER}, {@code CONTENT}. */
    @Column(nullable = false)
    String category;

    @Column(name = "target_type")
    String targetType;

    @Column(name = "target_id")
    String targetId;

    @Column(name = "http_method")
    String httpMethod;

    @Column(length = 512)
    String path;

    @Column(name = "status_code")
    Integer statusCode;

    /** {@code SUCCESS} / {@code FAILURE}. */
    @Column(nullable = false)
    String outcome;

    @Column(length = 64)
    String ip;

    @Column(name = "user_agent", length = 512)
    String userAgent;

    @Column(name = "latency_ms")
    Long latencyMs;

    @Column(nullable = false)
    String source;

    /** Free-form detail (reason, before/after summary). Reserved for richer Phase-2 events. */
    @Column(columnDefinition = "TEXT")
    String detail;
}
