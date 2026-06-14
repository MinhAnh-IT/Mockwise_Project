package com.interview.judge.admin.dto;

/**
 * Health/capacity snapshot of the upstream Judge0 server — the component behind
 * the 503-under-burst and queue-saturation incidents. Probed live per request.
 *
 * @param reachable     whether Judge0 answered the probe
 * @param latencyMs     probe round-trip latency
 * @param version       Judge0 version reported by {@code /about} (null if unknown)
 * @param queueSize     total enqueued submissions across worker queues
 * @param workersTotal  configured worker count
 * @param workersIdle   idle workers
 * @param workersWorking workers currently executing
 * @param detail        error text when not reachable
 */
public record Judge0Health(
        boolean reachable,
        Long latencyMs,
        String version,
        Integer queueSize,
        Integer workersTotal,
        Integer workersIdle,
        Integer workersWorking,
        String detail
) {}
