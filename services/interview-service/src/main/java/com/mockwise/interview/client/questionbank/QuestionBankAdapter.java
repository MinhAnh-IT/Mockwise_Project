package com.mockwise.interview.client.questionbank;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.questionbank.dto.FollowUpResponse;
import com.mockwise.interview.client.questionbank.dto.MarkAskedResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterRequest;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Wraps {@link QuestionBankClient} so callers see a clean domain-shaped
 * API instead of {@code ApiResponse} envelopes. Every method either
 * returns the unwrapped success payload or throws a {@link BusinessException}.
 *
 * <p>The {@code mark-asked} call is intentionally fire-and-forget: a
 * failure to bump the counter does not break the user-facing flow, so
 * we log and swallow rather than propagate. Everything else is hard-fail
 * because the orchestrator can't make a sensible decision without it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionBankAdapter {

    QuestionBankClient client;

    public QuestionFilterResponse filter(QuestionFilterRequest request) {
        return unwrap(client.filter(request), "filter");
    }

    public List<FollowUpResponse> findFollowUps(
            String parentQuestionId, String probesTargetKind, String probesTargetValue) {
        return unwrap(
                client.getFollowUps(parentQuestionId, probesTargetKind, probesTargetValue),
                "follow-ups");
    }

    /**
     * Soft-failing counter bump — the orchestrator must keep working even
     * if question-bank is briefly unreachable. Returns the new counter
     * snapshot when available, empty when the call failed.
     */
    public Optional<MarkAskedResponse> markAskedSoft(String questionId) {
        try {
            return Optional.of(unwrap(client.markAsked(questionId), "mark-asked"));
        } catch (Exception ex) {
            log.warn("mark-asked failed for question {}: {}", questionId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static <T> T unwrap(ApiResponse<T> response, String op) {
        if (response == null) {
            throw new BusinessException(StatusCode.QUESTION_BANK_UNAVAILABLE);
        }
        if (!response.isSuccess()) {
            throw new BusinessException(
                    response.getCode(),
                    response.getMessage() != null ? response.getMessage() : "question-bank " + op + " failed",
                    StatusCode.QUESTION_BANK_UNAVAILABLE.getHttpStatus());
        }
        return response.getData();
    }
}
