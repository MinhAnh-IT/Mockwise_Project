package com.mockwise.practice.service;

import com.core.apiresponse.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates Run/Submit against judge-service and owns the resulting history
 * + per-(user, problem) status.
 *
 * <ul>
 *   <li><b>Run</b> dispatches only the visible sample cases; it is <em>ephemeral</em>
 *       — held in {@link RunResultStore} for the poll window, never persisted, and
 *       it never touches problem status.</li>
 *   <li><b>Submit</b> dispatches the full case set (incl. hidden), persists history,
 *       and on verdict upserts ATTEMPTED → SOLVED.</li>
 *   <li>Verdict application ({@link #applyJudged}) routes to the DB row (SUBMIT) or
 *       the cached trial (RUN), and is idempotent — a terminal submission is left
 *       untouched on redelivery.</li>
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
    RunResultStore runCache;
    PracticeProperties properties;
    ObjectMapper objectMapper;

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
        boolean run = mode == SubmissionMode.RUN;

        PracticeSubmission submission = new PracticeSubmission();
        submission.setId(submissionId);
        submission.setUserId(userId);
        submission.setQuestionId(problemId);
        submission.setProblemTitle(detail.title());
        submission.setDifficulty(detail.difficulty());
        submission.setTags(detail.tags());
        submission.setLanguage(req.language().toLowerCase());
        submission.setSourceCode(req.code());
        submission.setMode(mode);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setTotalCases(selected.size());
        submission.setHiddenCaseIds(hiddenIds);

        // RUN is ephemeral (held in-memory for the poll window); only SUBMIT is persisted.
        if (run) {
            submission.setCreatedAt(LocalDateTime.now()); // no @PrePersist fires for a cached row
            runCache.put(new RunResult(submission));
        } else {
            submissionRepo.save(submission);
        }

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
            if (!run) submissionRepo.save(submission);
            throw new BusinessException(StatusCode.JUDGE_DISPATCH_FAILED);
        }

        submission.setStatus(SubmissionStatus.JUDGING);
        if (!run) submissionRepo.save(submission);
        return new SubmissionCreatedResponse(submissionId, SubmissionStatus.JUDGING);
    }

    // ── Verdict application (called by the Kafka consumer) ────────────────────

    /**
     * Applies a judge verdict to its submission. Routes to the persisted SUBMIT
     * row or, failing that, the cached ephemeral RUN trial. Idempotent: a
     * submission already DONE/FAILED is left untouched (Kafka is at-least-once).
     */
    @Transactional
    public void applyJudged(SubmissionJudgedEvent event) {
        Optional<PracticeSubmission> persisted = submissionRepo.findById(event.submissionId());
        if (persisted.isPresent()) {
            applyToSubmit(persisted.get(), event);
            return;
        }
        RunResult run = runCache.get(event.submissionId());
        if (run != null) {
            applyToRun(run, event);
            return;
        }
        log.warn("Verdict for unknown submission {} — ignoring", event.submissionId());
    }

    /** Persisted SUBMIT: write per-case rows, finalize the submission, upsert status. */
    private void applyToSubmit(PracticeSubmission submission, SubmissionJudgedEvent event) {
        if (isTerminal(submission)) return;
        List<PracticeSubmissionCase> cases = buildCases(submission, event);
        cases.forEach(caseRepo::save);
        applyVerdict(submission, cases, event.verdict());
        submissionRepo.save(submission);
        if (submission.getMode() == SubmissionMode.SUBMIT) {
            upsertProblemStatus(submission, event.verdict());
        }
    }

    /** Ephemeral RUN: finalize the in-memory trial only — nothing is persisted. */
    private void applyToRun(RunResult run, SubmissionJudgedEvent event) {
        PracticeSubmission submission = run.getSubmission();
        if (isTerminal(submission)) return;
        List<PracticeSubmissionCase> cases = buildCases(submission, event);
        run.setCases(cases);
        applyVerdict(submission, cases, event.verdict());
    }

    private boolean isTerminal(PracticeSubmission submission) {
        if (submission.getStatus() == SubmissionStatus.DONE
                || submission.getStatus() == SubmissionStatus.FAILED) {
            log.debug("Submission {} already terminal ({}) — skipping verdict",
                    submission.getId(), submission.getStatus());
            return true;
        }
        return false;
    }

    /**
     * Builds per-case rows (transient). The user's own stdout/stderr is stored
     * for every case — exposure of hidden-case I/O is gated at read time
     * ({@link #buildDetail}), where only a single failing case may be revealed.
     */
    private List<PracticeSubmissionCase> buildCases(PracticeSubmission submission, SubmissionJudgedEvent event) {
        Set<String> hiddenIds = submission.getHiddenCaseIds() == null
                ? Set.of()
                : Set.copyOf(submission.getHiddenCaseIds());
        List<SubmissionJudgedEvent.CaseResult> results = event.results() == null
                ? List.of()
                : event.results();

        List<PracticeSubmissionCase> cases = new ArrayList<>(results.size());
        int order = 0;
        for (SubmissionJudgedEvent.CaseResult r : results) {
            boolean hidden = r.testCaseId() != null && hiddenIds.contains(r.testCaseId());
            PracticeSubmissionCase c = new PracticeSubmissionCase();
            c.setId(UUID.randomUUID().toString());
            c.setSubmissionId(submission.getId());
            c.setOrderIndex(order++);
            c.setTestCaseId(r.testCaseId());
            c.setStatus(r.status());
            c.setRuntimeMs(r.runtimeMs());
            c.setMemoryKb(r.memoryKb());
            c.setHidden(hidden);
            c.setStdout(r.stdout());
            c.setStderr(r.stderr());
            cases.add(c);
        }
        return cases;
    }

    /** Folds per-case results into the submission's aggregate verdict + counters. */
    private void applyVerdict(PracticeSubmission submission, List<PracticeSubmissionCase> cases, String verdict) {
        int passed = 0;
        Integer maxRuntime = null;
        Integer maxMemory = null;
        for (PracticeSubmissionCase c : cases) {
            if (VERDICT_ACCEPTED.equalsIgnoreCase(c.getStatus())) passed++;
            maxRuntime = max(maxRuntime, c.getRuntimeMs());
            maxMemory = max(maxMemory, c.getMemoryKb());
        }
        submission.setVerdict(verdict);
        submission.setPassedCases(passed);
        submission.setTotalCases(cases.isEmpty() ? submission.getTotalCases() : cases.size());
        submission.setRuntimeMs(maxRuntime);
        submission.setMemoryKb(maxMemory);
        submission.setStatus(SubmissionStatus.DONE);
        submission.setFinishedAt(LocalDateTime.now());
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
        status.setTags(submission.getTags());
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
                mode,
                (verdict == null || verdict.isBlank()) ? null : verdict,
                PageRequest.of(page, size));

        List<SubmissionSummary> rows = p.getContent().stream().map(this::toSummary).toList();
        return PageResponse.of(rows, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    /** LeetCode "progress" view: the user's SUBMITs rolled up per problem, newest activity first. */
    @Transactional(readOnly = true)
    public List<ProblemSubmissionGroup> listProblemGroups(String userId) {
        return submissionRepo.groupByProblem(userId, VERDICT_ACCEPTED).stream()
                .map(PracticeSubmissionService::toGroup)
                .toList();
    }

    private static ProblemSubmissionGroup toGroup(Object[] row) {
        long total = ((Number) row[3]).longValue();
        long accepted = ((Number) row[4]).longValue();
        Integer bestRuntime = row[5] == null ? null : ((Number) row[5]).intValue();
        return new ProblemSubmissionGroup(
                (String) row[0],            // questionId
                (String) row[1],            // title
                (String) row[2],            // difficulty
                accepted > 0,               // solved
                total,
                accepted,
                bestRuntime,                // best accepted runtime (null until solved)
                (LocalDateTime) row[6],     // first solved at
                (LocalDateTime) row[7]);    // last submitted at
    }

    @Transactional(readOnly = true)
    public SubmissionDetail getSubmission(String userId, String id) {
        PracticeSubmission persisted = submissionRepo.findByIdAndUserId(id, userId).orElse(null);
        if (persisted != null) {
            return buildDetail(persisted, caseRepo.findBySubmissionIdOrderByOrderIndex(id));
        }
        // Fall back to an in-flight RUN trial (ownership-checked, never persisted).
        RunResult run = runCache.get(id);
        if (run != null && run.getSubmission().getUserId().equals(userId)) {
            return buildDetail(run.getSubmission(), run.getCases());
        }
        throw new BusinessException(StatusCode.SUBMISSION_NOT_FOUND);
    }

    private SubmissionDetail buildDetail(PracticeSubmission s, List<PracticeSubmissionCase> caseRows) {
        // Hidden-case I/O is never exposed in the full list — it leaks only via
        // the single revealed failing case below.
        List<SubmissionCaseView> cases = caseRows.stream()
                .map(c -> new SubmissionCaseView(
                        c.getOrderIndex(), c.getTestCaseId(), c.getStatus(),
                        c.getRuntimeMs(), c.getMemoryKb(), c.isHidden(),
                        c.isHidden() ? null : c.getStdout(),
                        c.isHidden() ? null : c.getStderr()))
                .toList();

        return new SubmissionDetail(
                s.getId(), s.getQuestionId(), s.getProblemTitle(), s.getLanguage(),
                s.getMode(), s.getStatus(), s.getVerdict(), s.getPassedCases(), s.getTotalCases(),
                s.getRuntimeMs(), s.getMemoryKb(), s.getSourceCode(),
                s.getCreatedAt(), s.getFinishedAt(), cases,
                buildRevealedCase(s, caseRows));
    }

    /**
     * On a non-accepted SUBMIT, reveals the <em>first</em> failing case so the
     * user can reproduce and fix it — input + expected (from question-bank) plus
     * their own output. Returns null for RUN, still-running, accepted, or
     * compile-error submissions (CE has no per-case input to show). The
     * question-bank lookup happens only on failure, so the cost is bounded.
     */
    private RevealedCase buildRevealedCase(PracticeSubmission s, List<PracticeSubmissionCase> caseRows) {
        if (s.getMode() != SubmissionMode.SUBMIT
                || s.getStatus() != SubmissionStatus.DONE
                || VERDICT_ACCEPTED.equalsIgnoreCase(s.getVerdict())) {
            return null;
        }

        PracticeSubmissionCase failing = caseRows.stream()
                .filter(c -> c.getTestCaseId() != null)
                .filter(PracticeSubmissionService::isRevealable)
                .min(Comparator.comparingInt(PracticeSubmissionCase::getOrderIndex))
                .orElse(null);
        if (failing == null) return null;

        try {
            QbCodingDetail detail = fetchDetail(s.getQuestionId());
            QbCodingDetail.QbTestCase tc = detail.testCases() == null ? null
                    : detail.testCases().stream()
                            .filter(t -> failing.getTestCaseId().equals(t.id()))
                            .findFirst().orElse(null);
            if (tc == null) return null;
            return new RevealedCase(
                    failing.getOrderIndex(),
                    toJson(tc.inputData()),
                    expectedValue(tc.expectedOutput()),
                    failing.getStdout(),
                    failing.getStatus());
        } catch (Exception e) {
            log.warn("Could not reveal failing case for submission {}: {}", s.getId(), e.getMessage());
            return null;
        }
    }

    /** A case worth revealing: ran and failed on its merits (not AC, not a compile error). */
    private static boolean isRevealable(PracticeSubmissionCase c) {
        String st = c.getStatus() == null ? "" : c.getStatus().toUpperCase();
        return !st.equals("AC") && !st.equals("CE") && !st.isEmpty();
    }

    /** Full JSON of the input map, e.g. {@code {"nums":[1,2,3]}}. */
    private String toJson(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return String.valueOf(map);
        }
    }

    /** Expected output: the bare value for a single-field result, else the whole map. */
    private String expectedValue(Map<String, Object> expected) {
        if (expected == null) return null;
        Object value = expected.size() == 1 ? expected.values().iterator().next() : expected;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
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
