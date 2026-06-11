package com.mockwise.practice.service;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.client.questionbank.QuestionBankClient;
import com.mockwise.practice.client.questionbank.dto.QbCodingDetail;
import com.mockwise.practice.common.config.PracticeProperties;
import com.mockwise.practice.common.exception.BusinessException;
import com.mockwise.practice.common.exception.StatusCode;
import com.mockwise.practice.dto.request.RunSubmitRequest;
import com.mockwise.practice.dto.response.*;
import com.mockwise.practice.entity.PracticeProblemStatus;
import com.mockwise.practice.entity.PracticeSubmission;
import com.mockwise.practice.entity.PracticeSubmissionCase;
import com.mockwise.practice.enums.ProblemStatus;
import com.mockwise.practice.enums.SubmissionMode;
import com.mockwise.practice.enums.SubmissionStatus;
import com.mockwise.practice.message.event.CodeSubmissionEvent;
import com.mockwise.practice.message.event.SubmissionJudgedEvent;
import com.mockwise.practice.message.producer.CodeSubmissionProducer;
import com.mockwise.practice.repository.PracticeProblemStatusRepository;
import com.mockwise.practice.repository.PracticeSubmissionCaseRepository;
import com.mockwise.practice.repository.PracticeSubmissionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates Run/Submit against judge-service and owns the resulting history
 * + per-(user, problem) status.
 *
 * <ul>
 *   <li><b>Run</b> dispatches only the visible sample cases; never touches status.</li>
 *   <li><b>Submit</b> dispatches the full case set (incl. hidden) and, on verdict,
 *       upserts ATTEMPTED → SOLVED.</li>
 *   <li>Verdict application ({@link #applyJudged}) is idempotent — a terminal
 *       submission is left untouched on redelivery.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeSubmissionService {

    /** Judge aggregate verdict that counts as solved. */
    private static final String VERDICT_ACCEPTED = "AC";

    QuestionBankClient questionBank;
    CodeSubmissionProducer producer;
    PracticeSubmissionRepository submissionRepo;
    PracticeSubmissionCaseRepository caseRepo;
    PracticeProblemStatusRepository statusRepo;
    PracticeProperties properties;

    // ── Run / Submit ────────────────────────────────────────────────────────

    /** Trial run against sample (non-hidden) cases only. */
    public SubmissionCreatedResponse run(String userId, String problemId, RunSubmitRequest req) {
        return dispatch(userId, problemId, req, SubmissionMode.RUN);
    }

    /** Graded run against the full case set (incl. hidden). */
    public SubmissionCreatedResponse submit(String userId, String problemId, RunSubmitRequest req) {
        return dispatch(userId, problemId, req, SubmissionMode.SUBMIT);
    }

    private SubmissionCreatedResponse dispatch(
            String userId, String problemId, RunSubmitRequest req, SubmissionMode mode) {

        validate(req);
        QbCodingDetail detail = fetchDetail(problemId);

        List<QbCodingDetail.QbTestCase> source = detail.testCases() == null
                ? List.of()
                : detail.testCases();
        // RUN executes only visible samples; SUBMIT executes everything.
        List<QbCodingDetail.QbTestCase> selected = mode == SubmissionMode.RUN
                ? source.stream().filter(tc -> !tc.hidden()).toList()
                : source;

        if (selected.isEmpty()) {
            // Nothing to execute — a misconfigured problem, surface as not-found.
            throw new BusinessException(StatusCode.PROBLEM_NOT_FOUND);
        }

        List<String> hiddenIds = selected.stream()
                .filter(QbCodingDetail.QbTestCase::hidden)
                .map(QbCodingDetail.QbTestCase::id)
                .toList();

        String submissionId = UUID.randomUUID().toString();
        PracticeSubmission submission = new PracticeSubmission();
        submission.setId(submissionId);
        submission.setUserId(userId);
        submission.setQuestionId(problemId);
        submission.setProblemTitle(detail.title());
        submission.setDifficulty(detail.difficulty());
        submission.setLanguage(req.language().toLowerCase());
        submission.setSourceCode(req.code());
        submission.setMode(mode);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setTotalCases(selected.size());
        submission.setHiddenCaseIds(hiddenIds);
        submissionRepo.save(submission);

        CodeSubmissionEvent event = new CodeSubmissionEvent(
                submissionId,
                "PRACTICE",
                submission.getLanguage(),
                req.code(),
                toEventFunctionMeta(detail.functionMeta()),
                selected.stream()
                        .map(tc -> new CodeSubmissionEvent.TestCase(tc.id(), tc.inputData(), tc.expectedOutput()))
                        .toList());

        try {
            producer.publish(event);
        } catch (Exception e) {
            log.error("Failed to dispatch submission {} to judge: {}", submissionId, e.getMessage());
            submission.setStatus(SubmissionStatus.FAILED);
            submission.setFinishedAt(LocalDateTime.now());
            submissionRepo.save(submission);
            throw new BusinessException(StatusCode.JUDGE_DISPATCH_FAILED);
        }

        submission.setStatus(SubmissionStatus.JUDGING);
        submissionRepo.save(submission);
        return new SubmissionCreatedResponse(submissionId, SubmissionStatus.JUDGING);
    }

    // ── Verdict application (called by the Kafka consumer) ────────────────────

    /**
     * Applies a judge verdict to its submission. Idempotent: a submission that
     * is already DONE/FAILED is left untouched (Kafka is at-least-once). Only
     * SUBMIT verdicts touch problem status.
     */
    @Transactional
    public void applyJudged(SubmissionJudgedEvent event) {
        Optional<PracticeSubmission> found = submissionRepo.findById(event.submissionId());
        if (found.isEmpty()) {
            log.warn("Verdict for unknown submission {} — ignoring", event.submissionId());
            return;
        }
        PracticeSubmission submission = found.get();
        if (submission.getStatus() == SubmissionStatus.DONE
                || submission.getStatus() == SubmissionStatus.FAILED) {
            log.debug("Submission {} already terminal ({}) — skipping verdict",
                    submission.getId(), submission.getStatus());
            return;
        }

        Set<String> hiddenIds = submission.getHiddenCaseIds() == null
                ? Set.of()
                : Set.copyOf(submission.getHiddenCaseIds());

        List<SubmissionJudgedEvent.CaseResult> results = event.results() == null
                ? List.of()
                : event.results();

        int passed = 0;
        Integer maxRuntime = null;
        Integer maxMemory = null;
        int order = 0;
        for (SubmissionJudgedEvent.CaseResult r : results) {
            boolean hidden = r.testCaseId() != null && hiddenIds.contains(r.testCaseId());
            if (VERDICT_ACCEPTED.equalsIgnoreCase(r.status())) passed++;
            maxRuntime = max(maxRuntime, r.runtimeMs());
            maxMemory = max(maxMemory, r.memoryKb());

            PracticeSubmissionCase c = new PracticeSubmissionCase();
            c.setId(UUID.randomUUID().toString());
            c.setSubmissionId(submission.getId());
            c.setOrderIndex(order++);
            c.setTestCaseId(r.testCaseId());
            c.setStatus(r.status());
            c.setRuntimeMs(r.runtimeMs());
            c.setMemoryKb(r.memoryKb());
            c.setHidden(hidden);
            // Hidden cases never expose their I/O.
            c.setStdout(hidden ? null : r.stdout());
            c.setStderr(hidden ? null : r.stderr());
            caseRepo.save(c);
        }

        submission.setVerdict(event.verdict());
        submission.setPassedCases(passed);
        submission.setTotalCases(results.isEmpty() ? submission.getTotalCases() : results.size());
        submission.setRuntimeMs(maxRuntime);
        submission.setMemoryKb(maxMemory);
        submission.setStatus(SubmissionStatus.DONE);
        submission.setFinishedAt(LocalDateTime.now());
        submissionRepo.save(submission);

        if (submission.getMode() == SubmissionMode.SUBMIT) {
            upsertProblemStatus(submission, event.verdict());
        }
    }

    private void upsertProblemStatus(PracticeSubmission submission, String verdict) {
        boolean accepted = VERDICT_ACCEPTED.equalsIgnoreCase(verdict);
        LocalDateTime now = LocalDateTime.now();

        PracticeProblemStatus status = statusRepo
                .findByUserIdAndQuestionId(submission.getUserId(), submission.getQuestionId())
                .orElseGet(() -> {
                    PracticeProblemStatus s = new PracticeProblemStatus();
                    s.setUserId(submission.getUserId());
                    s.setQuestionId(submission.getQuestionId());
                    s.setStatus(ProblemStatus.ATTEMPTED);
                    s.setAttemptCount(0);
                    return s;
                });

        status.setDifficulty(submission.getDifficulty());
        status.setAttemptCount(status.getAttemptCount() + 1);
        status.setLastAttemptAt(now);

        if (accepted) {
            if (status.getStatus() != ProblemStatus.SOLVED) {
                status.setStatus(ProblemStatus.SOLVED);
                status.setFirstSolvedAt(now);
            }
            // Track the best (lowest) accepted runtime.
            Integer rt = submission.getRuntimeMs();
            if (rt != null && (status.getBestRuntimeMs() == null || rt < status.getBestRuntimeMs())) {
                status.setBestRuntimeMs(rt);
            }
        }
        statusRepo.save(status);
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<SubmissionSummary> listSubmissions(
            String userId, String problemId, SubmissionMode mode, String verdict, int page, int size) {

        Page<PracticeSubmission> p = submissionRepo.search(
                userId,
                (problemId == null || problemId.isBlank()) ? null : problemId,
                mode == null ? null : mode.name(),
                (verdict == null || verdict.isBlank()) ? null : verdict,
                PageRequest.of(page, size));

        List<SubmissionSummary> rows = p.getContent().stream().map(this::toSummary).toList();
        return PageResponse.of(rows, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SubmissionDetail getSubmission(String userId, String id) {
        PracticeSubmission s = submissionRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(StatusCode.SUBMISSION_NOT_FOUND));

        List<SubmissionCaseView> cases = caseRepo.findBySubmissionIdOrderByOrderIndex(id).stream()
                .map(c -> new SubmissionCaseView(
                        c.getOrderIndex(), c.getTestCaseId(), c.getStatus(),
                        c.getRuntimeMs(), c.getMemoryKb(), c.isHidden(),
                        c.getStdout(), c.getStderr()))
                .toList();

        return new SubmissionDetail(
                s.getId(), s.getQuestionId(), s.getProblemTitle(), s.getLanguage(),
                s.getMode(), s.getStatus(), s.getVerdict(), s.getPassedCases(), s.getTotalCases(),
                s.getRuntimeMs(), s.getMemoryKb(), s.getSourceCode(),
                s.getCreatedAt(), s.getFinishedAt(), cases);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private SubmissionSummary toSummary(PracticeSubmission s) {
        return new SubmissionSummary(
                s.getId(), s.getQuestionId(), s.getProblemTitle(), s.getLanguage(),
                s.getMode(), s.getStatus(), s.getVerdict(),
                s.getPassedCases(), s.getTotalCases(), s.getRuntimeMs(), s.getCreatedAt());
    }

    private void validate(RunSubmitRequest req) {
        String lang = req.language() == null ? "" : req.language().toLowerCase();
        if (!properties.languageSet().contains(lang)) {
            throw new BusinessException(StatusCode.LANGUAGE_NOT_SUPPORTED, req.language());
        }
        int maxBytes = properties.run().maxCodeBytes();
        if (req.code() != null && req.code().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new BusinessException(StatusCode.CODE_TOO_LARGE);
        }
    }

    private QbCodingDetail fetchDetail(String problemId) {
        QbCodingDetail d = unwrap(questionBank.getProblem(problemId));
        if (d == null) {
            throw new BusinessException(StatusCode.PROBLEM_NOT_FOUND);
        }
        return d;
    }

    private static CodeSubmissionEvent.FunctionMeta toEventFunctionMeta(QbCodingDetail.QbFunctionMeta fm) {
        if (fm == null) return null;
        List<CodeSubmissionEvent.Param> params = fm.params() == null
                ? List.of()
                : fm.params().stream()
                        .map(p -> new CodeSubmissionEvent.Param(p.name(), p.type()))
                        .toList();
        return new CodeSubmissionEvent.FunctionMeta(
                fm.fn(), params, fm.returnType(), fm.orderMatters(), fm.inPlace());
    }

    private static Integer max(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    /** Mirror of {@code PracticeCatalogService.unwrap} — 2xx envelope that still reports failure. */
    private static <T> T unwrap(ApiResponse<T> response) {
        if (response == null || !response.isSuccess()) {
            throw new BusinessException(StatusCode.QUESTION_BANK_UNAVAILABLE);
        }
        return response.getData();
    }
}
