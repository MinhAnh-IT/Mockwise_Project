package com.mockwise.practice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds in-flight RUN trials for the brief poll window without touching the DB —
 * only graded SUBMITs are persisted. A RUN is dispatched, judged and polled on
 * the same node, so an in-memory map suffices for the single-instance deployment
 * (a multi-replica setup would need Redis instead). Entries are TTL-evicted so a
 * lost verdict can never leak memory.
 */
@Slf4j
@Component
public class RunResultStore {

    private static final long TTL_MS = Duration.ofMinutes(5).toMillis();

    private final Map<String, RunResult> store = new ConcurrentHashMap<>();

    public void put(RunResult result) {
        store.put(result.getSubmission().getId(), result);
    }

    /** Live entry, or null if absent/expired (an expired entry is dropped on access). */
    public RunResult get(String id) {
        RunResult r = store.get(id);
        if (r == null) {
            return null;
        }
        if (isExpired(r)) {
            store.remove(id);
            return null;
        }
        return r;
    }

    @Scheduled(fixedDelayString = "${practice.run-cache.sweep-ms:60000}")
    public void sweep() {
        int before = store.size();
        store.values().removeIf(this::isExpired);
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("RunResultStore evicted {} expired RUN trial(s)", removed);
        }
    }

    private boolean isExpired(RunResult r) {
        return System.currentTimeMillis() - r.getCachedAtMillis() > TTL_MS;
    }
}
