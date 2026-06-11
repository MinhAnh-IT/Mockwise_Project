package com.mockwise.practice.service;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.client.questionbank.QuestionBankClient;
import com.mockwise.practice.client.questionbank.dto.QbCodingDetail;
import com.mockwise.practice.client.questionbank.dto.QbPage;
import com.mockwise.practice.client.questionbank.dto.QbProblemSummary;
import com.mockwise.practice.common.exception.BusinessException;
import com.mockwise.practice.common.exception.StatusCode;
import com.mockwise.practice.dto.response.CodingProblemView;
import com.mockwise.practice.dto.response.PageResponse;
import com.mockwise.practice.dto.response.ProblemSummary;
import com.mockwise.practice.entity.PracticeProblemStatus;
import com.mockwise.practice.enums.ProblemStatus;
import com.mockwise.practice.repository.PracticeProblemStatusRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only catalog facade for the practice feature. Proxies question-bank's
 * internal coding-problem catalog and re-shapes it for the user:
 * <ul>
 *   <li>list → {@link ProblemSummary} (per-user {@code myStatus} + acceptance
 *       are placeholders until Phase 2/3);</li>
 *   <li>detail → {@link CodingProblemView} with hidden test cases stripped.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeCatalogService {

    QuestionBankClient questionBank;
    PracticeProblemStatusRepository statusRepo;

    /**
     * Paginated browse. Each row's {@code myStatus} is resolved from the
     * caller's {@code practice_problem_status} (SOLVED / ATTEMPTED, else NONE).
     */
    public PageResponse<ProblemSummary> listProblems(
            String userId, String difficulty, List<String> tags, String q, int page, int size) {

        QbPage<QbProblemSummary> qb = unwrap(
                questionBank.listProblems(difficulty, tags, q, page, size));

        List<String> ids = qb.content().stream().map(QbProblemSummary::id).toList();
        Map<String, ProblemStatus> statusByQuestion = ids.isEmpty()
                ? Map.of()
                : statusRepo.findByUserIdAndQuestionIdIn(userId, ids).stream()
                        .collect(Collectors.toMap(
                                PracticeProblemStatus::getQuestionId,
                                PracticeProblemStatus::getStatus,
                                (a, b) -> a));

        List<ProblemSummary> rows = qb.content().stream()
                .map(s -> toSummary(s, statusByQuestion.getOrDefault(s.id(), ProblemStatus.NONE)))
                .toList();

        return PageResponse.of(rows, qb.page(), qb.size(), qb.totalElements(), qb.totalPages());
    }

    /** Detail for the workspace — hidden test cases never leave this method. */
    public CodingProblemView getProblem(String userId, String id) {
        QbCodingDetail d = unwrap(questionBank.getProblem(id));
        if (d == null) {
            throw new BusinessException(StatusCode.PROBLEM_NOT_FOUND);
        }
        return toView(d);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private ProblemSummary toSummary(QbProblemSummary s, ProblemStatus myStatus) {
        return new ProblemSummary(
                s.id(),
                s.title(),
                s.difficulty(),
                s.tags(),
                s.optimalTimeComplexity(),
                s.optimalSpaceComplexity(),
                null,        // acceptanceRate (global per-problem) — future work
                myStatus);
    }

    private CodingProblemView toView(QbCodingDetail d) {
        CodingProblemView.FunctionMeta fnMeta = null;
        if (d.functionMeta() != null) {
            List<CodingProblemView.Param> params = d.functionMeta().params() == null
                    ? List.of()
                    : d.functionMeta().params().stream()
                            .map(p -> new CodingProblemView.Param(p.name(), p.type()))
                            .toList();
            fnMeta = new CodingProblemView.FunctionMeta(
                    d.functionMeta().fn(),
                    params,
                    d.functionMeta().returnType(),
                    d.functionMeta().orderMatters(),
                    d.functionMeta().inPlace());
        }

        List<CodingProblemView.SampleTestCase> samples = d.testCases() == null
                ? List.of()
                : d.testCases().stream()
                        .filter(tc -> !tc.hidden())   // drop hidden — never leaves the backend
                        .map(tc -> new CodingProblemView.SampleTestCase(
                                tc.id(), tc.inputData(), tc.expectedOutput(), tc.note()))
                        .toList();

        return new CodingProblemView(
                d.id(),
                d.title(),
                d.description(),
                d.constraints(),
                d.optimalTimeComplexity(),
                d.optimalSpaceComplexity(),
                fnMeta,
                d.starterCode() == null ? java.util.Map.of() : d.starterCode(),
                samples);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Non-2xx upstream responses are already turned into {@link BusinessException}
     * by the Feign {@code ErrorDecoder}; here we only guard a 2xx envelope that
     * still reports failure or carries no data.
     */
    private static <T> T unwrap(ApiResponse<T> response) {
        if (response == null || !response.isSuccess()) {
            throw new BusinessException(StatusCode.QUESTION_BANK_UNAVAILABLE);
        }
        return response.getData();
    }
}
