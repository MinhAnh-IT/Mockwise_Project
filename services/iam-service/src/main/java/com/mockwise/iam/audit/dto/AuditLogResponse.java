package com.mockwise.iam.audit.dto;

import com.mockwise.iam.audit.AuditLog;

import java.time.Instant;

/** Read model for one audit record on the admin screen. */
public record AuditLogResponse(
        Long id,
        Instant occurredAt,
        String actorId,
        String actorEmail,
        String actorRole,
        String action,
        String category,
        String targetType,
        String targetId,
        String httpMethod,
        String path,
        Integer statusCode,
        String outcome,
        String ip,
        String userAgent,
        Long latencyMs,
        String source,
        String detail
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
                a.getId(), a.getOccurredAt(), a.getActorId(), a.getActorEmail(), a.getActorRole(),
                a.getAction(), a.getCategory(), a.getTargetType(), a.getTargetId(),
                a.getHttpMethod(), a.getPath(), a.getStatusCode(), a.getOutcome(),
                a.getIp(), a.getUserAgent(), a.getLatencyMs(), a.getSource(), a.getDetail());
    }
}
