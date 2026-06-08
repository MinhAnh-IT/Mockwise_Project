package com.mockwise.gateway.monitoring;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Snapshot for the admin server-monitoring tab. Assembled fresh on each request
 * by probing services, infrastructure, and the host (the gateway container's
 * JVM view, which on a single-VPS deploy approximates the host).
 */
public record MonitoringOverview(
        List<ComponentStatus> services,
        List<ComponentStatus> infra,
        HostMetrics host,
        RuntimeInfo runtime,
        OffsetDateTime generatedAt
) {
    /** status is one of UP / DEGRADED / DOWN. latencyMs null when DOWN. */
    public record ComponentStatus(String name, String status, Long latencyMs, String detail) {}

    public record HostMetrics(
            Double cpuLoadPercent,
            Double systemLoadAverage,
            long memTotalBytes,
            long memUsedBytes,
            long diskTotalBytes,
            long diskFreeBytes
    ) {}

    public record RuntimeInfo(
            String service,
            String javaVersion,
            int availableProcessors,
            long uptimeMs,
            OffsetDateTime startedAt
    ) {}
}
