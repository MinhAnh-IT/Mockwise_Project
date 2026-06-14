package com.mockwise.iam.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Wire contract for the {@code audit-log} Kafka topic. Two producers emit this
 * shape as plain JSON:
 *
 * <ul>
 *   <li><b>API gateway</b> — a coarse access audit for every mutating
 *       {@code /admin/**} request. It fills the raw HTTP facts
 *       ({@code httpMethod}, {@code path}, {@code statusCode}, {@code ip}, …)
 *       and leaves {@code action}/{@code category}/{@code targetType}/
 *       {@code targetId}/{@code outcome} null — the consumer derives those from
 *       the method + path via {@link AuditClassifier}.</li>
 *   <li><b>IAM auth</b> — login / logout events, which are not {@code /admin/}
 *       routes, so it sets {@code action}/{@code category}/{@code outcome}
 *       explicitly.</li>
 * </ul>
 *
 * <p>Kept structurally identical to the gateway's copy of this POJO; JSON is the
 * contract (there is no shared source module to host it).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditEvent {

    /** Origin of the event: {@code "gateway"} or {@code "iam-auth"}. */
    String source;

    String actorId;
    String actorEmail;
    String actorRole;

    /** Set by IAM auth; null from the gateway (derived by the consumer). */
    String action;
    String category;
    String targetType;
    String targetId;

    String httpMethod;
    String path;
    Integer statusCode;

    /** {@code SUCCESS} / {@code FAILURE}; null from the gateway (derived from statusCode). */
    String outcome;

    String ip;
    String userAgent;
    Long latencyMs;

    /** ISO-8601 instant string (e.g. {@code 2026-06-14T10:30:00Z}). Plain text on the
     *  wire so no producer needs a Jackson JavaTime module. */
    String occurredAt;

    /** Optional free-form detail (reason, before/after summary). Reserved for richer Phase-2 events. */
    String detail;
}
