package com.interview.judge.admin.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Rolled-up judge throughput for the admin dashboard, computed over a time
 * window plus a few all-time live signals (backlog).
 *
 * @param window        the requested window label (e.g. {@code "24h"})
 * @param since         window start (UTC)
 * @param total         jobs created within the window
 * @param byStatus      job count per lifecycle status (PENDING/RUNNING/DONE/FAILED)
 * @param byVerdict     job count per verdict (AC/WA/RE/CE/TLE/MLE/UNKNOWN)
 * @param backlog       jobs currently PENDING or RUNNING (all-time, live)
 * @param avgLatencyMs  mean create→finish latency over finished jobs in the window
 * @param p95LatencyMs  95th-percentile create→finish latency
 * @param retriedJobs   jobs that needed ≥1 Judge0 resubmit (transient errors)
 * @param maxRetryCount highest retry count seen in the window
 */
public record JudgeStatsResponse(
        String window,
        OffsetDateTime since,
        long total,
        Map<String, Long> byStatus,
        Map<String, Long> byVerdict,
        long backlog,
        Double avgLatencyMs,
        Long p95LatencyMs,
        long retriedJobs,
        int maxRetryCount,
        OffsetDateTime generatedAt
) {}
