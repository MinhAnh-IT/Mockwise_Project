package com.interview.judge.controller;

import com.interview.judge.dto.SubmissionEvent;
import com.interview.judge.entity.JudgeTaskResult;
import com.interview.judge.repository.JudgeJobRepository;
import com.interview.judge.repository.JudgeTaskResultRepository;
import com.interview.judge.service.JudgeOrchestrator;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Test-only controller that bypasses Kafka.
 *
 * <p>Use this to submit code directly and poll for results without
 * needing a running Kafka broker.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code POST /api/v1/judge/submit}         — submit a job</li>
 *   <li>{@code GET  /api/v1/judge/status/{submissionId}} — poll job status</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/judge")
@Slf4j
@RequiredArgsConstructor
public class SubmitController {

    private final JudgeOrchestrator judgeOrchestrator;
    private final JudgeJobRepository judgeJobRepository;
    private final JudgeTaskResultRepository judgeTaskResultRepository;

    /**
     * Accepts a {@link SubmissionEvent} body and triggers the judge pipeline directly.
     *
     * <p>If {@code submissionId} is omitted in the request body, a random UUID is generated.
     *
     * <p>Example request body — see the Kafka message format in the README / prompt doc.
     *
     * @return {@code 202 Accepted} with the generated {@code submissionId} to use for polling
     */
    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submit(@RequestBody SubmissionEvent event) {
        if (event.getSubmissionId() == null) {
            event.setSubmissionId(UUID.randomUUID());
        }

        log.info("[TEST] Direct submit: submissionId={}, language={}, testCases={}",
                event.getSubmissionId(), event.getLanguage(),
                event.getTestCases() != null ? event.getTestCases().size() : 0);

        judgeOrchestrator.handle(event);

        return ResponseEntity.accepted().body(
                SubmitResponse.builder()
                        .submissionId(event.getSubmissionId())
                        .message("Submission accepted. Poll /api/v1/judge/status/{submissionId} for results.")
                        .build()
        );
    }

    /**
     * Returns the current status of a job and all its task results.
     *
     * <p>Poll this endpoint until {@code status} is {@code DONE} or {@code FAILED}.
     *
     * @param submissionId the UUID returned by {@code POST /api/v1/judge/submit}
     * @return {@code 200} with job details, or {@code 404} if not found
     */
    @GetMapping("/status/{submissionId}")
    public ResponseEntity<JobStatusResponse> getStatus(@PathVariable UUID submissionId) {
        return judgeJobRepository.findBySubmissionId(submissionId)
                .map(job -> {
                    List<JudgeTaskResult> tasks =
                            judgeTaskResultRepository.findByJobOrderByOrderIndex(job);

                    List<TaskResultView> taskViews = tasks.stream()
                            .map(t -> TaskResultView.builder()
                                    .testCaseId(t.getTestCaseId())
                                    .orderIndex(t.getOrderIndex())
                                    .status(t.getStatus().name())
                                    .stdout(t.getStdout())
                                    .stderr(t.getStderr())
                                    .runtimeMs(t.getRuntimeMs())
                                    .memoryKb(t.getMemoryKb())
                                    .build())
                            .collect(Collectors.toList());

                    JobStatusResponse response = JobStatusResponse.builder()
                            .jobId(job.getId())
                            .submissionId(job.getSubmissionId())
                            .status(job.getStatus().name())
                            .verdict(job.getVerdict())
                            .doneCases(job.getDoneCases())
                            .totalCases(job.getTotalCases())
                            .createdAt(job.getCreatedAt().toString())
                            .finishedAt(job.getFinishedAt() != null ? job.getFinishedAt().toString() : null)
                            .results(taskViews)
                            .build();

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Response DTOs ─────────────────────────────────────────────

    @Getter
    @Builder
    public static class SubmitResponse {
        private UUID submissionId;
        private String message;
    }

    @Getter
    @Builder
    public static class JobStatusResponse {
        private UUID jobId;
        private UUID submissionId;
        private String status;
        private String verdict;
        private int doneCases;
        private int totalCases;
        private String createdAt;
        private String finishedAt;
        private List<TaskResultView> results;
    }

    @Getter
    @Builder
    public static class TaskResultView {
        private UUID testCaseId;
        private int orderIndex;
        private String status;
        private String stdout;
        private String stderr;
        private Integer runtimeMs;
        private Integer memoryKb;
    }
}
