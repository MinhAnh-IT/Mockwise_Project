package com.mockwise.interview.dto.admin.response;

import java.util.Map;

/**
 * Aggregate overview of interview sessions for the admin dashboard.
 *
 * @param totalSessions total session rows
 * @param byStatus      count per {@link com.mockwise.interview.enums.SessionStatus} name
 * @param byType        count per {@link com.mockwise.interview.enums.InterviewType} name
 *                      (key {@code "UNKNOWN"} buckets rows with a null/legacy type)
 * @param averageScore  mean {@code finalScore} across scored sessions, or null when none scored
 */
public record AdminSessionStatsResponse(
        long totalSessions,
        Map<String, Long> byStatus,
        Map<String, Long> byType,
        Double averageScore
) {}
