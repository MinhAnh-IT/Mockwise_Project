package com.interview.judge.controller;

import com.interview.judge.judge0.Judge0CallbackPayload;
import com.interview.judge.service.JudgeOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/judge/callback")
@Slf4j
@RequiredArgsConstructor
public class Judge0CallbackController {

    private final JudgeOrchestrator judgeOrchestrator;

    /**
     * Receives the execution result of a job's single batched Judge0 submission.
     *
     * <p>With compile-once batching there is exactly one Judge0 submission — and
     * thus one callback — per job, routed by {@code jobId}.
     *
     * @param jobId   UUID of the {@link com.interview.judge.entity.JudgeJob}
     * @param payload Judge0 callback body (stdout/stderr are base64-encoded)
     * @return 200 OK on success, 404 if the job is unknown, 500 on processing error
     */
    @RequestMapping(value = "/job/{jobId}", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Void> callback(
            @PathVariable UUID jobId,
            @RequestBody Judge0CallbackPayload payload) {

        log.info("Judge0 callback received: jobId={}, judge0Status={}",
                jobId, payload.getStatus() != null ? payload.getStatus().getId() : "null");

        try {
            judgeOrchestrator.handleCallback(jobId, payload);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error("Job not found in callback: jobId={}", jobId, e);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error handling Judge0 callback: jobId={}", jobId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
