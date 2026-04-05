package com.interview.judge.service;

import com.interview.judge.codebuilder.CodeBuilder;
import com.interview.judge.codebuilder.OutputComparator;
import com.interview.judge.codebuilder.StdinBuilder;
import com.interview.judge.dto.SubmissionEvent;
import com.interview.judge.dto.TestCaseDto;
import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.JudgeTaskResult;
import com.interview.judge.entity.enums.JobStatus;
import com.interview.judge.entity.enums.TaskStatus;
import com.interview.judge.judge0.Judge0CallbackPayload;
import com.interview.judge.judge0.Judge0Client;
import com.interview.judge.kafka.JudgeResultProducer;
import com.interview.judge.mapper.JudgeMapper;
import com.interview.judge.repository.JudgeJobRepository;
import com.interview.judge.repository.JudgeTaskResultRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JudgeOrchestrator {

    final JudgeJobRepository judgeJobRepository;
    final JudgeTaskResultRepository judgeTaskResultRepository;
    final CodeBuilder codeBuilder;
    final StdinBuilder stdinBuilder;
    final Judge0Client judge0Client;
    final VerdictAggregator verdictAggregator;
    final JudgeResultProducer judgeResultProducer;
    final OutputComparator outputComparator;
    final JudgeMapper judgeMapper;
    final TransactionTemplate transactionTemplate;

    @Value("${judge.callback-base-url}")
    String callbackBaseUrl;

    /**
     * Entry point called by {@link com.interview.judge.kafka.SubmissionConsumer}.
     * Creates the JudgeJob, builds source code, and submits each test case to Judge0.
     *
     * <p>Phase 1 (transactional): persist JudgeJob + all JudgeTaskResult rows and commit.
     * Phase 2 (no outer transaction): submit each task to Judge0.
     * This ordering guarantees the DB rows exist before any Judge0 callback arrives.
     */
    public void handle(SubmissionEvent event) {
        log.info("Handling submission: submissionId={}, language={}, testCases={}",
                event.getSubmissionId(), event.getLanguage(), event.getTestCases().size());

        // Phase 1: persist job + task shells — commit BEFORE any Judge0 HTTP call
        List<JudgeTaskResult> tasks = new ArrayList<>();
        JudgeJob job = transactionTemplate.execute(status -> {
            JudgeJob j = judgeMapper.toJudgeJob(event);
            judgeJobRepository.save(j);
            log.info("JudgeJob created: id={}", j.getId());

            List<TestCaseDto> testCases = event.getTestCases();
            for (int i = 0; i < testCases.size(); i++) {
                JudgeTaskResult task = judgeMapper.toJudgeTaskResult(j, testCases.get(i), i);
                judgeTaskResultRepository.save(task);
                tasks.add(task);
                log.debug("JudgeTaskResult created: id={}, testCaseId={}, orderIndex={}",
                        task.getId(), testCases.get(i).getId(), i);
            }
            return j;
        });
        // Transaction committed — DB rows are now visible to callback handler

        // Phase 2: build source and submit to Judge0 (outside transaction)
        String fullSource = codeBuilder.buildFullSource(event.getCode(), event.getLanguage());
        log.info("full code: " + fullSource);

        int localFailures = 0;
        List<TestCaseDto> testCases = event.getTestCases();
        for (int i = 0; i < tasks.size(); i++) {
            boolean failed = submitTaskToJudge0(job, tasks.get(i), testCases.get(i), fullSource, event);
            if (failed) localFailures++;
        }

        // If all tasks failed locally (Judge0 never called), finalize now
        if (localFailures > 0) {
            job = judgeJobRepository.findById(job.getId()).orElse(job);
            if (job.getDoneCases() >= job.getTotalCases() && job.getTotalCases() > 0) {
                log.info("All tasks failed locally — finalizing job immediately: jobId={}", job.getId());
                finalizeJob(job);
            }
        }
    }

    /**
     * Builds stdin, submits to Judge0, and persists the token.
     *
     * @return true if the task failed locally (no Judge0 submission was made)
     */
    private boolean submitTaskToJudge0(JudgeJob job, JudgeTaskResult task, TestCaseDto tc,
                                       String fullSource, SubmissionEvent event) {
        // Build stdin
        String stdin;
        try {
            stdin = stdinBuilder.build(event.getFunctionMeta(), tc.getInputData());
        } catch (Exception e) {
            log.error("Failed to build stdin for testCase {}: {}", tc.getId(), e.getMessage());
            UUID taskId = task.getId();
            transactionTemplate.execute(status -> {
                JudgeTaskResult freshTask = judgeTaskResultRepository.findById(taskId).orElseThrow();
                markTaskFailed(freshTask, "Failed to build stdin: " + e.getMessage());
                judgeJobRepository.incrementDoneCases(job.getId());
                return null;
            });
            return true;
        }

        // Submit to Judge0 async
        UUID taskId = task.getId();
        String callbackUrl = callbackBaseUrl + "/api/v1/judge/callback/" + taskId;
        try {
            String judge0Token = judge0Client.submitAsync(fullSource, stdin, callbackUrl, event.getLanguage());
            transactionTemplate.execute(status -> {
                JudgeTaskResult freshTask = judgeTaskResultRepository.findById(taskId).orElseThrow();
                freshTask.setJudge0Token(judge0Token);
                judgeTaskResultRepository.save(freshTask);
                return null;
            });
            log.info("Submitted to Judge0: taskId={}, token={}", taskId, judge0Token);
            return false;
        } catch (Exception e) {
            log.error("Failed to submit to Judge0 for taskId={}: {}", taskId, e.getMessage());
            transactionTemplate.execute(status -> {
                JudgeTaskResult freshTask = judgeTaskResultRepository.findById(taskId).orElseThrow();
                markTaskFailed(freshTask, "Failed to submit to Judge0: " + e.getMessage());
                judgeJobRepository.incrementDoneCases(job.getId());
                return null;
            });
            return true;
        }
    }

    private void markTaskFailed(JudgeTaskResult task, String errorMessage) {
        task.setStatus(TaskStatus.RE);
        task.setStderr(errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        judgeTaskResultRepository.save(task);
    }

    /**
     * Called by {@link com.interview.judge.controller.Judge0CallbackController}
     * when Judge0 POSTs the execution result back.
     *
     * <p>Uses an atomic SQL increment for {@code doneCases} to avoid the lost-update
     * problem that occurs with read-modify-write under concurrent callbacks.
     * Finalization is guarded by a conditional status transition so only one callback
     * can finalize the job even if multiple complete simultaneously.
     */
    @Transactional
    public void handleCallback(UUID taskId, Judge0CallbackPayload payload) {
        log.info("Handling Judge0 callback: taskId={}, judge0Status={}",
                taskId, payload.getStatus() != null ? payload.getStatus().getId() : "null");

        JudgeTaskResult task = judgeTaskResultRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("JudgeTaskResult not found: " + taskId));

        UUID jobId = task.getJob().getId();

        // Load job to read FunctionMeta (needed for verdict logic) — no locking needed
        JudgeJob job = judgeJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("JudgeJob not found: " + jobId));

        // Decode base64 fields
        String stdout        = decodeBase64(payload.getStdout());
        String stderr        = decodeBase64(payload.getStderr());
        String compileOutput = decodeBase64(payload.getCompileOutput());

        // Parse runtime
        Integer runtimeMs = parseRuntimeMs(payload.getTime());

        // Determine task status
        int judge0StatusId = payload.getStatus() != null ? payload.getStatus().getId() : -1;
        boolean orderMatters = job.getFunctionMeta().isOrderMatters();
        TaskStatus taskStatus = resolveTaskStatus(
                judge0StatusId, stdout, task.getExpectedOutput(), orderMatters);

        // Update task result
        task.setStdout(stdout != null ? stdout.trim() : null);
        task.setStderr(judge0StatusId == 6 ? compileOutput : stderr);
        task.setStatus(taskStatus);
        task.setRuntimeMs(runtimeMs);
        task.setMemoryKb(payload.getMemory());
        task.setFinishedAt(LocalDateTime.now());
        judgeTaskResultRepository.save(task);

        log.info("JudgeTaskResult updated: taskId={}, status={}", taskId, taskStatus);

        // Atomic increment — avoids lost-update when multiple callbacks commit concurrently
        judgeJobRepository.incrementDoneCases(jobId);

        // Re-read to get the authoritative doneCases after our increment
        JudgeJob updated = judgeJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("JudgeJob not found after increment: " + jobId));

        log.info("Job progress: jobId={}, done={}/{}", jobId, updated.getDoneCases(), updated.getTotalCases());

        if (updated.getDoneCases() >= updated.getTotalCases()) {
            // Guard: only finalize if status is still RUNNING (prevents double-finalization)
            int won = judgeJobRepository.markDoneIfRunning(jobId, JobStatus.RUNNING, JobStatus.DONE);
            if (won > 0) {
                finalizeJob(updated);
            }
        }
    }

    private void finalizeJob(JudgeJob job) {
        log.info("Finalizing job: jobId={}", job.getId());

        // Re-read fresh from DB — the entity may be detached after clearAutomatically
        JudgeJob freshJob = judgeJobRepository.findById(job.getId())
                .orElseThrow(() -> new IllegalStateException("JudgeJob not found during finalization: " + job.getId()));

        List<JudgeTaskResult> results = judgeTaskResultRepository.findByJobOrderByOrderIndex(freshJob);
        String verdict = verdictAggregator.aggregate(results);

        freshJob.setVerdict(verdict);
        freshJob.setFinishedAt(LocalDateTime.now());
        judgeJobRepository.save(freshJob);

        log.info("Job finalized: jobId={}, verdict={}", freshJob.getId(), verdict);

        judgeResultProducer.publish(judgeMapper.toJudgeResultEvent(freshJob, results));
    }

    private TaskStatus resolveTaskStatus(int judge0StatusId, String stdout,
                                         String expectedOutputJson, boolean orderMatters) {
        return switch (judge0StatusId) {
            case 3 -> {  // Judge0 Accepted → compare output
                boolean correct = outputComparator.compare(stdout, expectedOutputJson, orderMatters);
                log.debug("Output comparison: correct={}", correct);
                yield correct ? TaskStatus.AC : TaskStatus.WA;
            }
            case 5  -> TaskStatus.TLE;
            case 6  -> TaskStatus.CE;
            case 7, 8, 9, 10, 11, 12 -> TaskStatus.RE;
            case 13, 14 -> TaskStatus.RE;
            default -> {
                log.warn("Unknown Judge0 status id: {} — treating as RE", judge0StatusId);
                yield TaskStatus.RE;
            }
        };
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            return new String(Base64.getDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to base64-decode value, returning as-is");
            return encoded;
        }
    }

    private Integer parseRuntimeMs(String timeSeconds) {
        if (timeSeconds == null) return null;
        try {
            return (int) (Double.parseDouble(timeSeconds) * 1000);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Judge0 time: {}", timeSeconds);
            return null;
        }
    }
}
