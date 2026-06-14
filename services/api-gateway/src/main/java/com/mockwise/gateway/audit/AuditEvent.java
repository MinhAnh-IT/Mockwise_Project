package com.mockwise.gateway.audit;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Wire contract for the {@code audit-log} Kafka topic. The gateway fills the raw
 * HTTP facts and leaves the semantic fields ({@code action}, {@code category},
 * {@code targetType}, {@code targetId}, {@code outcome}) null — IAM's consumer
 * derives them from method + path. Structurally identical to IAM's copy of this
 * POJO; JSON is the contract (there is no shared source module to host it).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditEvent {

    String source;

    String actorId;
    String actorEmail;
    String actorRole;

    String action;
    String category;
    String targetType;
    String targetId;

    String httpMethod;
    String path;
    Integer statusCode;

    String outcome;

    String ip;
    String userAgent;
    Long latencyMs;

    /** ISO-8601 instant string — plain text on the wire (no JavaTime module needed). */
    String occurredAt;

    String detail;
}
