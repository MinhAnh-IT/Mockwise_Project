package com.mockwise.questionbank.service;

import com.mockwise.questionbank.common.exception.BusinessException;
import com.mockwise.questionbank.common.exception.StatusCode;
import com.mockwise.questionbank.dto.response.CodingProblemSummary;
import com.mockwise.questionbank.dto.response.CodingQuestionResponse;
import com.mockwise.questionbank.dto.response.PageResponse;
import com.mockwise.questionbank.entity.CodingQuestion;
import com.mockwise.questionbank.entity.Question;
import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.QuestionStatus;
import com.mockwise.questionbank.enums.QuestionType;
import com.mockwise.questionbank.mapper.CodingMapper;
import com.mockwise.questionbank.repository.CodingQuestionRepository;
import com.mockwise.questionbank.repository.QuestionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Read-only catalog facade for the practice (LeetCode-style) feature.
 * Surfaces ONLY {@code ACTIVE} coding problems and is consumed exclusively by
 * practice-service over the internal network (see
 * {@code InternalCodingProblemController}).
 *
 * <p>The detail payload here is the <b>full</b> {@link CodingQuestionResponse}
 * including hidden test cases — privacy stripping happens in practice-service
 * before anything reaches a user (practice needs the hidden cases to build a
 * Submit run against the judge).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CodingCatalogService {

    CodingQuestionRepository codingRepository;
    QuestionRepository questionRepository;
    CodingMapper codingMapper;

    /**
     * Paginated browse over ACTIVE coding problems. All filters optional:
     * {@code difficulty} exact, {@code tags} overlap, {@code q} title search.
     */
    @Transactional(readOnly = true)
    public PageResponse<CodingProblemSummary> listProblems(
            Difficulty difficulty, List<String> tags, String q, Pageable pageable) {

        Page<CodingProblemSummary> page = codingRepository.findActiveProblems(
                        difficulty == null ? null : difficulty.name(),
                        toPostgresArray(tags),
                        (q == null || q.isBlank()) ? null : q.trim(),
                        pageable)
                .map(this::toSummary);

        return PageResponse.from(page);
    }

    /**
     * Full detail for one ACTIVE coding problem. 404s on unknown id, on a
     * non-coding question, or on a problem that is not ACTIVE — the practice
     * catalog must never expose drafts or retired problems.
     */
    @Transactional(readOnly = true)
    public CodingQuestionResponse getDetail(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        if (base.getType() != QuestionType.LIVE_CODING
                || base.getStatus() != QuestionStatus.ACTIVE) {
            throw new BusinessException(StatusCode.QUESTION_NOT_FOUND);
        }

        CodingQuestion cq = codingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        return codingMapper.toResponse(cq);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CodingProblemSummary toSummary(CodingQuestion cq) {
        Question base = cq.getQuestion();
        return new CodingProblemSummary(
                base.getId(),
                cq.getTitle(),
                base.getDifficulty(),
                base.getTags() == null ? List.of() : Arrays.asList(base.getTags()),
                cq.getOptimalTimeComplexity(),
                cq.getOptimalSpaceComplexity());
    }

    /** Mirrors {@code QuestionService.toPostgresArray} — {@code null} disables the filter. */
    private String toPostgresArray(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return "{" + String.join(",", tags) + "}";
    }
}
