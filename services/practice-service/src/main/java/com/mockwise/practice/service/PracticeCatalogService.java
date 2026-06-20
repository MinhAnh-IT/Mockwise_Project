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
import com.mockwise.practice.repository.PracticeSubmissionRepository;
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
 *   <li>list → {@link ProblemSummary} with per-user {@code myStatus} and the
 *       global {@code acceptanceRate} (accepted/graded SUBMITs) backed in;</li>
 *   <li>detail → {@link CodingProblemView} with hidden test cases stripped.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeCatalogService {

    /** Judge aggregate verdict that counts as accepted — mirrors the submission service. */
    private static final String VERDICT_ACCEPTED = "AC";

    QuestionBankClient questionBank;
    PracticeProblemStatusRepository statusRepo;
    PracticeSubmissionRepository submissionRepo;

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

        Map<String, Double> acceptanceByQuestion = acceptanceRates(ids);

        List<ProblemSummary> rows = qb.content().stream()
                .map(s -> toSummary(
                        s,
                        acceptanceByQuestion.get(s.id()),
                        statusByQuestion.getOrDefault(s.id(), ProblemStatus.NONE)))
                .toList();

        return PageResponse.of(rows, qb.page(), qb.size(), qb.totalElements(), qb.totalPages());
    }

    /** Detail for the workspace — hidden test cases never leave this method. */
    public CodingProblemView getProblem(String userId, String id) {
        QbCodingDetail d = unwrap(questionBank.getProblem(id));
        if (d == null) {
            throw new BusinessException(StatusCode.PROBLEM_NOT_FOUND);
        }
        ProblemStatus myStatus = statusRepo.findByUserIdAndQuestionId(userId, id)
                .map(PracticeProblemStatus::getStatus)
                .orElse(ProblemStatus.NONE);
        return toView(d, myStatus);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private ProblemSummary toSummary(QbProblemSummary s, Double acceptanceRate, ProblemStatus myStatus) {
        return new ProblemSummary(
                s.id(),
                s.title(),
                s.difficulty(),
                s.tags(),
                s.optimalTimeComplexity(),
                s.optimalSpaceComplexity(),
                acceptanceRate,   // global accepted/graded SUBMITs; null until first graded submit
                myStatus);
    }

    /**
     * Global acceptance ratio (0..1) per question over graded SUBMITs. Questions
     * with no graded submit yet are simply absent from the map → {@code null} on
     * the row. Computed in one batched aggregate for the whole page.
     */
    private Map<String, Double> acceptanceRates(List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return submissionRepo.aggregateAcceptance(ids, VERDICT_ACCEPTED).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> {
                            long total = ((Number) row[1]).longValue();
                            long accepted = ((Number) row[2]).longValue();
                            return (double) accepted / total;   // total ≥ 1 (GROUP BY)
                        }));
    }

    private CodingProblemView toView(QbCodingDetail d, ProblemStatus myStatus) {
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
                samples,
                myStatus);
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
