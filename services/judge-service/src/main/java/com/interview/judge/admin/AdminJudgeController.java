package com.interview.judge.admin;

import com.interview.judge.admin.dto.DlqOverview;
import com.interview.judge.admin.dto.JudgeJobDetail;
import com.interview.judge.admin.dto.JudgeJobSummary;
import com.interview.judge.admin.dto.JudgeStatsResponse;
import com.interview.judge.admin.dto.Judge0Health;
import com.interview.judge.admin.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin Judge-monitoring API. Mapped under {@code /api/v1/judge/admin/**} so the
 * api-gateway both routes it (matches the judge-service {@code /api/v1/judge/**}
 * route) and enforces ROLE_ADMIN (the gateway gates any path containing
 * {@code /admin/}). All endpoints are read-only.
 */
@RestController
@RequestMapping("/api/v1/judge/admin")
@RequiredArgsConstructor
public class AdminJudgeController {

    private final AdminJudgeService adminJudgeService;
    private final DlqInspectionService dlqInspectionService;
    private final Judge0HealthProbe judge0HealthProbe;

    /** Throughput / verdict / latency rollup over the given window (default 24h). */
    @GetMapping("/stats")
    public ApiEnvelope<JudgeStatsResponse> stats(
            @RequestParam(required = false, defaultValue = "24h") String window) {
        return ApiEnvelope.success(adminJudgeService.stats(window));
    }

    /** Paged job table, newest first, with optional status / verdict filters. */
    @GetMapping("/jobs")
    public ApiEnvelope<PageResponse<JudgeJobSummary>> jobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verdict,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiEnvelope.success(adminJudgeService.jobs(status, verdict, page, size));
    }

    /** Drill-down for one job (by submissionId) with all per-test-case results. */
    @GetMapping("/jobs/{submissionId}")
    public ResponseEntity<ApiEnvelope<JudgeJobDetail>> jobDetail(@PathVariable UUID submissionId) {
        return adminJudgeService.jobDetail(submissionId)
                .map(detail -> ResponseEntity.ok(ApiEnvelope.success(detail)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Dead-letter topic snapshot: total + most recent messages (read-only). */
    @GetMapping("/dlq")
    public ApiEnvelope<DlqOverview> dlq(@RequestParam(defaultValue = "50") int limit) {
        return ApiEnvelope.success(dlqInspectionService.overview(limit));
    }

    /** Live Judge0 capacity/health probe (queue depth, workers, version). */
    @GetMapping("/judge0")
    public ApiEnvelope<Judge0Health> judge0() {
        return ApiEnvelope.success(judge0HealthProbe.probe());
    }
}
