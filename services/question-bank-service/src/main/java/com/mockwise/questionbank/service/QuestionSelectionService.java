package com.mockwise.questionbank.service;

import com.mockwise.questionbank.common.exception.BusinessException;
import com.mockwise.questionbank.common.exception.StatusCode;
import com.mockwise.questionbank.dto.request.QuestionFilterRequest;
import com.mockwise.questionbank.dto.response.FollowUpResponse;
import com.mockwise.questionbank.dto.response.MarkAskedResponse;
import com.mockwise.questionbank.dto.response.QuestionCandidateResponse;
import com.mockwise.questionbank.dto.response.QuestionFilterResponse;
import com.mockwise.questionbank.entity.BehavioralQuestion;
import com.mockwise.questionbank.entity.CoreQuestion;
import com.mockwise.questionbank.entity.Question;
import com.mockwise.questionbank.entity.QuestionFollowUp;
import com.mockwise.questionbank.enums.QuestionType;
import com.mockwise.questionbank.repository.BehavioralQuestionRepository;
import com.mockwise.questionbank.repository.CoreQuestionRepository;
import com.mockwise.questionbank.repository.QuestionFollowUpRepository;
import com.mockwise.questionbank.repository.QuestionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Selection-time queries for interview-service. Kept separate from
 * {@link QuestionService} (which owns CRUD + downstream payloads) so the
 * surface invoked by the orchestrator is small and obvious.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionSelectionService {

    QuestionRepository questionRepository;
    BehavioralQuestionRepository behavioralRepository;
    CoreQuestionRepository coreRepository;
    QuestionFollowUpRepository followUpRepository;

    public QuestionFilterResponse filter(QuestionFilterRequest req) {
        validate(req);

        // Tags use the "IS NULL OR overlap" pattern, so pass null (not "{}")
        // when there's no bias — '{} && q.tags' is always FALSE and would
        // wipe out the candidate pool. excludeIds uses NOT ANY which works
        // correctly with "{}" so it stays.
        String tags = (req.tagsAny() == null || req.tagsAny().isEmpty())
                ? null
                : toPgTextArray(req.tagsAny());
        String excludeIds = toPgTextArray(req.excludeIds());
        int limit = req.effectiveLimit();

        List<QuestionCandidateResponse> candidates = switch (req.type()) {
            case BEHAVIORAL -> behavioralRepository.findCandidates(
                            req.competency(),
                            req.difficulty() != null ? req.difficulty().name() : null,
                            req.requireOpener(),
                            tags,
                            excludeIds,
                            limit)
                    .stream()
                    .map(QuestionSelectionService::toBehavioralCandidate)
                    .toList();
            case CORE_CONCEPTUAL -> coreRepository.findCandidates(
                            req.domain(),
                            req.targetRole(),
                            req.difficulty() != null ? req.difficulty().name() : null,
                            req.requireOpener(),
                            tags,
                            excludeIds,
                            limit)
                    .stream()
                    .map(QuestionSelectionService::toCoreCandidate)
                    .toList();
            default -> throw new BusinessException(StatusCode.FILTER_TYPE_NOT_SUPPORTED);
        };

        return new QuestionFilterResponse(candidates, candidates.size());
    }

    @Transactional
    public MarkAskedResponse markAsked(String questionId) {
        int updated = questionRepository.markAsked(questionId);
        if (updated == 0) {
            throw new BusinessException(StatusCode.QUESTION_NOT_FOUND);
        }
        // Re-read to return the new counter — the UPDATE doesn't return rows
        // but the round-trip is cheap and gives the caller something useful.
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
        return new MarkAskedResponse(q.getId(), q.getAskCount(), q.getLastAskedAt());
    }

    public List<FollowUpResponse> findFollowUps(String parentQuestionId,
                                                String probesTargetKind,
                                                String probesTargetValue) {
        if (!questionRepository.existsById(parentQuestionId)) {
            throw new BusinessException(StatusCode.FOLLOW_UP_PARENT_NOT_FOUND);
        }

        List<QuestionFollowUp> matches;
        if (probesTargetKind != null && probesTargetValue != null) {
            Optional<QuestionFollowUp> exact = followUpRepository
                    .findByParentQuestionIdAndProbesTargetKindAndProbesTargetValue(
                            parentQuestionId, probesTargetKind, probesTargetValue);
            matches = exact.map(List::of).orElseGet(List::of);
        } else {
            matches = followUpRepository.findByParentQuestionIdOrderByCreatedAtAsc(parentQuestionId);
        }

        return matches.stream().map(QuestionSelectionService::toFollowUpResponse).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void validate(QuestionFilterRequest req) {
        switch (req.type()) {
            case BEHAVIORAL -> {
                if (req.competency() == null || req.competency().isBlank()) {
                    throw new BusinessException(StatusCode.FILTER_MISSING_COMPETENCY);
                }
            }
            case CORE_CONCEPTUAL -> {
                if (req.domain() == null || req.domain().isBlank()) {
                    throw new BusinessException(StatusCode.FILTER_MISSING_DOMAIN);
                }
            }
            default -> throw new BusinessException(StatusCode.FILTER_TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * Builds a Postgres {@code text[]} literal. Native queries use
     * {@code CAST(:p AS text[])} so we emit a string here instead of
     * relying on the JDBC driver to translate {@code List<String>}.
     * Returns {@code "{}"} for null/empty so the {@code = ANY} predicate
     * harmlessly matches nothing.
     */
    private static String toPgTextArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            String v = values.get(i);
            // Quote items containing commas/braces/quotes; safe to skip otherwise
            // since our values are enum names or UUIDs.
            if (v != null && (v.contains(",") || v.contains("{") || v.contains("}") || v.contains("\""))) {
                sb.append('"').append(v.replace("\"", "\\\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static QuestionCandidateResponse toBehavioralCandidate(BehavioralQuestion bq) {
        Question q = bq.getQuestion();
        return new QuestionCandidateResponse(
                bq.getId(),
                QuestionType.BEHAVIORAL,
                q.getDifficulty(),
                bq.getText(),
                bq.getCompetency() != null ? bq.getCompetency().name() : null,
                null,
                q.getTags() != null ? Arrays.asList(q.getTags()) : List.of(),
                bq.getAudioKey(),
                Boolean.TRUE.equals(bq.getIsOpener()),
                q.getAskCount() != null ? q.getAskCount() : 0L);
    }

    private static QuestionCandidateResponse toCoreCandidate(CoreQuestion cq) {
        Question q = cq.getQuestion();
        return new QuestionCandidateResponse(
                cq.getId(),
                QuestionType.CORE_CONCEPTUAL,
                q.getDifficulty(),
                cq.getText(),
                null,
                cq.getDomain() != null ? cq.getDomain().name() : null,
                q.getTags() != null ? Arrays.asList(q.getTags()) : List.of(),
                cq.getAudioKey(),
                Boolean.TRUE.equals(cq.getIsOpener()),
                q.getAskCount() != null ? q.getAskCount() : 0L);
    }

    private static FollowUpResponse toFollowUpResponse(QuestionFollowUp fu) {
        return new FollowUpResponse(
                fu.getId(),
                fu.getParentQuestionId(),
                fu.getProbesTargetKind(),
                fu.getProbesTargetValue(),
                fu.getText(),
                fu.getExpectedPoints(),
                fu.getAudioKey());
    }
}
