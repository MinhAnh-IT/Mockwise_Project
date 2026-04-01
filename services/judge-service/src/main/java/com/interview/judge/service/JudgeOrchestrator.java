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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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

    @Value("${judge.callback-base-url}")
    String callbackBaseUrl;

    /**
     * Entry point called by {@link com.interview.judge.kafka.SubmissionConsumer}.
     * Creates the JudgeJob, builds source code, and submits each test case to Judge0.
     */
    @Transactional
    public void handle(SubmissionEvent event) {
        log.info("Handling submission: submissionId={}, language={}, testCases={}",
                event.getSubmissionId(), event.getLanguage(), event.getTestCases().size());

        // 1. Create JudgeJob via mapper
        JudgeJob job = judgeMapper.toJudgeJob(event);
        judgeJobRepository.save(job);
        log.info("JudgeJob created: id={}", job.getId());

        // 2. Build full source (user code + UniversalDriver)
        String fullSource = codeBuilder.buildFullSource(event.getCode(), event.getLanguage());
        log.info("full code: " + fullSource);
        // 3. Submit each test case to Judge0
        List<TestCaseDto> testCases = event.getTestCases();
        for (int i = 0; i < testCases.size(); i++) {
            TestCaseDto tc = testCases.get(i);
            processTestCase(job, tc, i, fullSource, event);
        }

        // If all tasks failed locally (no Judge0 submissions), finalize now
        judgeJobRepository.save(job);
        if (job.getDoneCases() >= job.getTotalCases() && job.getTotalCases() > 0) {
            log.info("All tasks failed locally — finalizing job immediately: jobId={}", job.getId());
            finalizeJob(job);
        }
    }

    private void processTestCase(JudgeJob job, TestCaseDto tc, int index,
                                 String fullSource, SubmissionEvent event) {
        // 3a. Create JudgeTaskResult via mapper
        JudgeTaskResult task = judgeMapper.toJudgeTaskResult(job, tc, index);
        judgeTaskResultRepository.save(task);
        log.debug("JudgeTaskResult created: id={}, testCaseId={}, orderIndex={}", task.getId(), tc.getId(), index);

        // 3b. Build stdin
        String stdin;
        try {
            stdin = stdinBuilder.build(event.getFunctionMeta(), tc.getInputData());
        } catch (Exception e) {
            log.error("Failed to build stdin for testCase {}: {}", tc.getId(), e.getMessage());
            markTaskFailed(task, "Failed to build stdin: " + e.getMessage());
            job.setDoneCases(job.getDoneCases() + 1);
            return;
        }

        // 3c. Submit to Judge0 async
        String callbackUrl = callbackBaseUrl + "/api/callback/" + task.getId();
        try {
            String judge0Token = judge0Client.submitAsync(fullSource, stdin, callbackUrl, event.getLanguage());
            task.setJudge0Token(judge0Token);
            judgeTaskResultRepository.save(task);
            log.info("Submitted to Judge0: taskId={}, token={}", task.getId(), judge0Token);
        } catch (Exception e) {
            log.error("Failed to submit to Judge0 for taskId={}: {}", task.getId(), e.getMessage());
            markTaskFailed(task, "Failed to submit to Judge0: " + e.getMessage());
            job.setDoneCases(job.getDoneCases() + 1);
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
     */
    @Transactional
    public void handleCallback(UUID taskId, Judge0CallbackPayload payload) {
        log.info("Handling Judge0 callback: taskId={}, judge0Status={}",
                taskId, payload.getStatus() != null ? payload.getStatus().getId() : "null");

        JudgeTaskResult task = judgeTaskResultRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("JudgeTaskResult not found: " + taskId));

        UUID jobId = task.getJob().getId();

        // Acquire pessimistic lock on job to safely update doneCases
        JudgeJob job = judgeJobRepository.findByIdForUpdate(jobId)
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
        // For CE, the error is in compile_output; otherwise stderr
        task.setStderr(judge0StatusId == 6 ? compileOutput : stderr);
        task.setStatus(taskStatus);
        task.setRuntimeMs(runtimeMs);
        task.setMemoryKb(payload.getMemory());
        task.setFinishedAt(LocalDateTime.now());
        judgeTaskResultRepository.save(task);

        log.info("JudgeTaskResult updated: taskId={}, status={}", taskId, taskStatus);

        // Increment doneCases (safe due to pessimistic lock)
        job.setDoneCases(job.getDoneCases() + 1);
        judgeJobRepository.save(job);

        log.info("Job progress: jobId={}, done={}/{}", job.getId(), job.getDoneCases(), job.getTotalCases());

        if (job.getDoneCases() >= job.getTotalCases()) {
            finalizeJob(job);
        }
    }

    private void finalizeJob(JudgeJob job) {
        log.info("Finalizing job: jobId={}", job.getId());

        List<JudgeTaskResult> results = judgeTaskResultRepository.findByJobOrderByOrderIndex(job);
        String verdict = verdictAggregator.aggregate(results);

        job.setVerdict(verdict);
        job.setStatus(JobStatus.DONE);
        job.setFinishedAt(LocalDateTime.now());
        judgeJobRepository.save(job);

        log.info("Job finalized: jobId={}, verdict={}", job.getId(), verdict);

        judgeResultProducer.publish(judgeMapper.toJudgeResultEvent(job, results));
    }

    private TaskStatus resolveTaskStatus(int judge0StatusId, String stdout,
                                         String expectedOutputJson, boolean orderMatters) {
        return switch (judge0StatusId) {
            case 3 -> {  // Judge0 Accepted → compare output
                boolean correct = outputComparator.compare(stdout, expectedOutputJson, orderMatters);
                log.debug("Output comparison: correct={}", correct);
                yield correct ? TaskStatus.AC : TaskStatus.WA;
            }
            case 6  -> TaskStatus.CE;
            case 11 -> TaskStatus.TLE;
            case 12 -> TaskStatus.MLE;
            case 13, 14, 15 -> TaskStatus.RE;
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
