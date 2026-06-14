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
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
    final EntityManager entityManager;

    @Value("${judge.callback-base-url}")
    String callbackBaseUrl;

    /** Resubmit attempts when a Judge0 callback reports a transient internal error (status 13/14). */
    @Value("${judge0.exec-max-retries:2}")
    int execMaxRetries;

    /** Record separator framing each case's output in the batch protocol (matches the Universal*Driver files). */
    private static final String RS = "\u001e";

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

        List<TestCaseDto> testCases = event.getTestCases();

        // Phase 1: persist job + per-case task shells — commit BEFORE any Judge0
        // HTTP call so the rows exist before the (single) callback can arrive.
        JudgeJob job = transactionTemplate.execute(status -> {
            JudgeJob j = judgeMapper.toJudgeJob(event);
            judgeJobRepository.save(j);
            log.info("JudgeJob created: id={}", j.getId());
            for (int i = 0; i < testCases.size(); i++) {
                judgeTaskResultRepository.save(judgeMapper.toJudgeTaskResult(j, testCases.get(i), i));
            }
            return j;
        });
        UUID jobId = job.getId();

        // Phase 2: compile-once batch — build the full source + one stdin holding
        // every case, then submit a SINGLE Judge0 submission for the whole job.
        String fullSource = codeBuilder.buildFullSource(
                event.getCode(), event.getLanguage(), event.getFunctionMeta());

        String batchStdin;
        try {
            List<Map<String, Object>> inputs = new ArrayList<>(testCases.size());
            for (TestCaseDto tc : testCases) {
                inputs.add(tc.getInputData());
            }
            batchStdin = stdinBuilder.buildBatch(event.getFunctionMeta(), inputs);
        } catch (Exception e) {
            log.error("Failed to build batch stdin for jobId={}: {}", jobId, e.getMessage());
            failEntireJob(jobId, TaskStatus.RE, "Failed to build stdin: " + e.getMessage());
            return;
        }

        // Persist the payload so a transient Judge0 internal error can be retried
        // by resubmitting the identical source + stdin (see handleCallback).
        transactionTemplate.execute(s -> {
            JudgeJob j = judgeJobRepository.findById(jobId).orElseThrow();
            j.setFullSource(fullSource);
            j.setBatchStdin(batchStdin);
            judgeJobRepository.save(j);
            return null;
        });

        submitJobToJudge0(jobId, fullSource, batchStdin, event.getLanguage());
    }

    /**
     * Submits the whole job as ONE Judge0 submission (callback routed by jobId).
     * Judge0's queue-full / transport failures are already retried inside
     * {@link Judge0Client#submitAsync}; if it still throws, the job is finalized RE.
     */
    private void submitJobToJudge0(UUID jobId, String fullSource, String batchStdin, String language) {
        String callbackUrl = callbackBaseUrl + "/api/v1/judge/callback/job/" + jobId;
        try {
            String token = judge0Client.submitAsync(fullSource, batchStdin, callbackUrl, language);
            transactionTemplate.execute(s -> {
                JudgeJob j = judgeJobRepository.findById(jobId).orElseThrow();
                j.setJudge0Token(token);
                judgeJobRepository.save(j);
                return null;
            });
            log.info("Submitted job to Judge0: jobId={}, token={}", jobId, token);
        } catch (Exception e) {
            log.error("Failed to submit job to Judge0: jobId={}: {}", jobId, e.getMessage());
            failEntireJob(jobId, TaskStatus.RE, "Failed to submit to Judge0: " + e.getMessage());
        }
    }

    /** Marks every still-PENDING case with {@code status} and finalizes the job. */
    private void failEntireJob(UUID jobId, TaskStatus status, String stderr) {
        transactionTemplate.execute(s -> {
            JudgeJob job = judgeJobRepository.findByIdForUpdate(jobId).orElseThrow();
            if (job.getStatus() == JobStatus.DONE) {
                return null;
            }
            LocalDateTime now = LocalDateTime.now();
            List<JudgeTaskResult> tasks = judgeTaskResultRepository.findByJobOrderByOrderIndex(job);
            for (JudgeTaskResult t : tasks) {
                if (t.getStatus() == TaskStatus.PENDING) {
                    t.setStatus(status);
                    t.setStderr(stderr);
                    t.setFinishedAt(now);
                }
            }
            judgeTaskResultRepository.saveAll(tasks);
            entityManager.detach(job);   // avoid JSON dirty-check flush on markDoneIfRunning
            if (judgeJobRepository.markDoneIfRunning(jobId, JobStatus.RUNNING, JobStatus.DONE) > 0) {
                finalizeJob(job);
            }
            return null;
        });
    }

    /**
     * Called by {@link com.interview.judge.controller.Judge0CallbackController}
     * when Judge0 POSTs the result of a job's single batched submission.
     *
     * <p>One submission ⇒ one callback per job, so the per-task lost-update
     * machinery is gone. Flow:
     * <ol>
     *   <li>claim the callback under a row lock (ignore duplicates / already-DONE);</li>
     *   <li>a transient Judge0 internal error (status 13/14) resubmits the same
     *       payload up to {@code execMaxRetries} times — the HTTP resubmit runs
     *       OUTSIDE the transaction so it never holds the job's row lock;</li>
     *   <li>otherwise parse the batched stdout into per-case verdicts, finalize.</li>
     * </ol>
     */
    public void handleCallback(UUID jobId, Judge0CallbackPayload payload) {
        int statusId = payload.getStatus() != null ? payload.getStatus().getId() : -1;
        log.info("Handling Judge0 batch callback: jobId={}, judge0Status={}", jobId, statusId);

        // Phase 1 (txn): claim the callback and decide retry vs. terminal.
        Boolean retry = transactionTemplate.execute(s -> {
            JudgeJob job = judgeJobRepository.findByIdForUpdate(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("JudgeJob not found: " + jobId));
            if (job.getStatus() == JobStatus.DONE) {
                log.warn("Ignoring duplicate/late callback for job {} (already DONE)", jobId);
                return null;
            }
            if ((statusId == 13 || statusId == 14) && job.getRetryCount() < execMaxRetries) {
                job.setRetryCount(job.getRetryCount() + 1);
                judgeJobRepository.save(job);
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        });

        if (retry == null) {
            return;                       // duplicate / already finalized
        }
        if (retry) {                      // transient Judge0 internal error → resubmit (outside any tx)
            JudgeJob job = judgeJobRepository.findById(jobId).orElseThrow();
            log.warn("Judge0 internal error (status {}) — resubmitting jobId={} (retry {}/{})",
                    statusId, jobId, job.getRetryCount(), execMaxRetries);
            submitJobToJudge0(jobId, job.getFullSource(), job.getBatchStdin(), job.getLanguage());
            return;
        }

        // Phase 2 (txn): apply per-case verdicts and finalize.
        transactionTemplate.execute(s -> {
            JudgeJob job = judgeJobRepository.findByIdForUpdate(jobId).orElseThrow();
            if (job.getStatus() == JobStatus.DONE) {
                return null;
            }
            applyBatchResults(job, statusId, payload);
            if (judgeJobRepository.markDoneIfRunning(jobId, JobStatus.RUNNING, JobStatus.DONE) > 0) {
                finalizeJob(job);
            }
            return null;
        });
    }

    /**
     * Maps the single batched Judge0 result onto the job's per-case rows.
     * <ul>
     *   <li>status 6 (CE) → every case CE;</li>
     *   <li>status 13/14 (internal, retries exhausted) → every case RE;</li>
     *   <li>otherwise split the RS-framed stdout into per-case bodies and grade
     *       each (OK→AC/WA via {@link OutputComparator}, ERR→RE). Cases with no
     *       frame — the process timed out (status 5 → TLE) or hard-crashed
     *       (segfault/OOM → RE) before reaching them — take the tail status.</li>
     * </ul>
     */
    private void applyBatchResults(JudgeJob job, int statusId, Judge0CallbackPayload payload) {
        boolean orderMatters = job.getFunctionMeta().isOrderMatters();
        entityManager.detach(job);   // read JSON meta, then detach to avoid dirty-check flush

        List<JudgeTaskResult> tasks = judgeTaskResultRepository.findByJobOrderByOrderIndex(job);
        LocalDateTime now = LocalDateTime.now();
        Integer runtimeMs = parseRuntimeMs(payload.getTime());   // whole-batch totals
        Integer memoryKb = payload.getMemory();

        if (statusId == 6) {                       // compile error → all CE
            String ce = decodeBase64(payload.getCompileOutput());
            for (JudgeTaskResult t : tasks) {
                t.setStatus(TaskStatus.CE);
                t.setStderr(ce);
                t.setFinishedAt(now);
            }
        } else if (statusId == 13 || statusId == 14) {   // internal error, retries exhausted
            for (JudgeTaskResult t : tasks) {
                t.setStatus(TaskStatus.RE);
                t.setStderr("Judge0 internal error");
                t.setFinishedAt(now);
            }
        } else {                                   // 3 (ran) / 5 (TLE) / 7-12 (runtime) → parse frames
            List<String[]> frames = parseFramed(decodeBase64(payload.getStdout()));
            TaskStatus tail = statusId == 5 ? TaskStatus.TLE : TaskStatus.RE;
            for (int i = 0; i < tasks.size(); i++) {
                JudgeTaskResult t = tasks.get(i);
                if (i < frames.size()) {
                    String frameStatus = frames.get(i)[0];
                    String body = frames.get(i)[1];
                    if ("OK".equals(frameStatus)) {
                        boolean correct = outputComparator.compare(body, t.getExpectedOutput(), orderMatters);
                        t.setStatus(correct ? TaskStatus.AC : TaskStatus.WA);
                        t.setStdout(body != null ? body.trim() : null);
                    } else {
                        t.setStatus(TaskStatus.RE);
                        t.setStderr(body);
                    }
                } else {
                    t.setStatus(tail);   // process terminated before reaching this case
                    if (tail == TaskStatus.RE) {
                        t.setStderr("no output (process terminated before this case)");
                    }
                }
                t.setRuntimeMs(runtimeMs);
                t.setMemoryKb(memoryKb);
                t.setFinishedAt(now);
            }
        }
        judgeTaskResultRepository.saveAll(tasks);
    }

    /**
     * Splits RS-framed batch stdout into ordered {@code [status, body]} pairs.
     * Each frame is {@code \x1e} + ("OK"|"ERR") + {@code \n} + body. The body runs
     * until the next {@code \x1e} (so multi-line outputs are preserved).
     */
    private List<String[]> parseFramed(String raw) {
        List<String[]> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String chunk : raw.split(RS, -1)) {
            if (chunk.isEmpty()) {
                continue;            // leading empty segment before the first RS
            }
            int nl = chunk.indexOf('\n');
            if (nl < 0) {
                out.add(new String[]{chunk.trim(), ""});
            } else {
                out.add(new String[]{chunk.substring(0, nl).trim(), chunk.substring(nl + 1)});
            }
        }
        return out;
    }

    private void finalizeJob(JudgeJob job) {
        log.info("Finalizing job: jobId={}", job.getId());

        // Re-read fresh from DB — the entity may be detached after clearAutomatically
        JudgeJob freshJob = judgeJobRepository.findById(job.getId())
                .orElseThrow(() -> new IllegalStateException("JudgeJob not found during finalization: " + job.getId()));

        List<JudgeTaskResult> results = judgeTaskResultRepository.findByJobOrderByOrderIndex(freshJob);
        String verdict = verdictAggregator.aggregate(results);

        freshJob.setVerdict(verdict);
        freshJob.setDoneCases(freshJob.getTotalCases());
        freshJob.setFinishedAt(LocalDateTime.now());
        judgeJobRepository.save(freshJob);

        log.info("Job finalized: jobId={}, verdict={}", freshJob.getId(), verdict);

        // Ephemeral (validation) jobs have no downstream consumer — they're polled
        // once via /status and then purged — so don't emit a verdict event.
        if (freshJob.isEphemeral()) {
            log.debug("Ephemeral job {} — skipping verdict publish", freshJob.getId());
            return;
        }
        judgeResultProducer.publish(judgeMapper.toJudgeResultEvent(freshJob, results));
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            // MIME decoder, NOT the basic decoder: Judge0 returns base64 in MIME
            // form (RFC 2045), which inserts a line separator every 76 chars once
            // the payload exceeds ~57 bytes of output. Base64.getDecoder() rejects
            // those newlines and throws, which previously fell through to the
            // catch and stored the raw (still-encoded) base64 as stdout — making
            // every test case whose correct output is longer than ~57 bytes a
            // spurious WA. getMimeDecoder() ignores line separators (and any other
            // non-alphabet chars), so both wrapped and unwrapped payloads decode.
            return new String(Base64.getMimeDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
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
