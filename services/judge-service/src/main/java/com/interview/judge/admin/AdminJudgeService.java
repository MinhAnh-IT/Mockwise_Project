package com.interview.judge.admin;

import com.interview.judge.admin.dto.JudgeJobDetail;
import com.interview.judge.admin.dto.JudgeJobSummary;
import com.interview.judge.admin.dto.JudgeStatsResponse;
import com.interview.judge.admin.dto.PageResponse;
import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.JudgeTaskResult;
import com.interview.judge.entity.enums.JobStatus;
import com.interview.judge.repository.JudgeJobRepository;
import com.interview.judge.repository.JudgeTaskResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only rollups and drill-downs over the judge tables for the admin
 * Judge-monitoring dashboard. No writes — purely observational.
 */
@Service
@RequiredArgsConstructor
public class AdminJudgeService {

    private final JudgeJobRepository jobRepository;
    private final JudgeTaskResultRepository taskResultRepository;

    /** Lifecycle statuses considered "in flight" for the live backlog count. */
    private static final List<JobStatus> BACKLOG_STATUSES =
            List.of(JobStatus.PENDING, JobStatus.RUNNING);

    @Transactional(readOnly = true)
    public JudgeStatsResponse stats(String window) {
        Duration span = parseWindow(window);
        LocalDateTime since = LocalDateTime.now().minus(span);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (JobStatus s : JobStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        long total = 0;
        for (Object[] row : jobRepository.countByStatusSince(since)) {
            JobStatus status = (JobStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status.name(), count);
            total += count;
        }

        Map<String, Long> byVerdict = new LinkedHashMap<>();
        for (Object[] row : jobRepository.countByVerdictSince(since)) {
            String verdict = row[0] == null ? "UNKNOWN" : (String) row[0];
            long count = ((Number) row[1]).longValue();
            byVerdict.merge(verdict, count, Long::sum);
        }

        long backlog = jobRepository.countByStatusIn(BACKLOG_STATUSES);

        // Latency percentiles computed in Java to stay portable across MySQL/H2.
        List<Long> latencies = new ArrayList<>();
        for (Object[] row : jobRepository.finishedTimestampsSince(since)) {
            LocalDateTime created = (LocalDateTime) row[0];
            LocalDateTime finished = (LocalDateTime) row[1];
            if (created != null && finished != null) {
                latencies.add(Duration.between(created, finished).toMillis());
            }
        }
        Double avgLatencyMs = latencies.isEmpty() ? null
                : latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        Long p95LatencyMs = percentile(latencies, 95);

        return new JudgeStatsResponse(
                window == null ? "24h" : window,
                since.atOffset(ZoneOffset.UTC),
                total,
                byStatus,
                byVerdict,
                backlog,
                avgLatencyMs,
                p95LatencyMs,
                jobRepository.countRetriedSince(since),
                jobRepository.maxRetryCountSince(since),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<JudgeJobSummary> jobs(String status, String verdict, int page, int size) {
        JobStatus statusFilter = status == null || status.isBlank()
                ? null : JobStatus.valueOf(status.toUpperCase());
        String verdictFilter = verdict == null || verdict.isBlank()
                ? null : verdict.toUpperCase();

        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<JudgeJob> result = jobRepository.search(
                statusFilter, verdictFilter, PageRequest.of(Math.max(page, 0), safeSize));
        return PageResponse.of(result.map(JudgeJobSummary::from));
    }

    @Transactional(readOnly = true)
    public Optional<JudgeJobDetail> jobDetail(UUID submissionId) {
        return jobRepository.findBySubmissionId(submissionId)
                .map(job -> {
                    List<JudgeTaskResult> tasks = taskResultRepository.findByJobOrderByOrderIndex(job);
                    return JudgeJobDetail.from(job, tasks);
                });
    }

    /** Linear-interpolation-free nearest-rank percentile; null on empty input. */
    private static Long percentile(List<Long> values, int p) {
        if (values.isEmpty()) return null;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        int idx = Math.min(Math.max(rank - 1, 0), sorted.size() - 1);
        return sorted.get(idx);
    }

    /** Parses {@code 30m / 1h / 24h / 7d}; defaults to 24h on null/garbage. */
    static Duration parseWindow(String window) {
        if (window == null || window.isBlank()) return Duration.ofHours(24);
        String w = window.trim().toLowerCase();
        try {
            char unit = w.charAt(w.length() - 1);
            long amount = Long.parseLong(w.substring(0, w.length() - 1));
            return switch (unit) {
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                default -> Duration.ofHours(24);
            };
        } catch (RuntimeException e) {
            return Duration.ofHours(24);
        }
    }
}
