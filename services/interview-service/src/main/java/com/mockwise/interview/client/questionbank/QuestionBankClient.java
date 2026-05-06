package com.mockwise.interview.client.questionbank;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.questionbank.config.QuestionBankFeignConfig;
import com.mockwise.interview.client.questionbank.dto.FollowUpResponse;
import com.mockwise.interview.client.questionbank.dto.MarkAskedResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterRequest;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionSnapshotResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign view onto the question-bank-service surface this service depends on.
 * The {@code url} property must include the {@code /questions} segment
 * because question-bank's controllers are mounted there
 * (e.g. {@code POST /api/v1/question-bank/questions/filter}); the
 * {@code /internal} routes use a different controller mounted at
 * {@code /internal/questions} — we expose them via the same client to
 * keep the surface concentrated.
 */
@FeignClient(
        name = "question-bank-service",
        url = "${external.services.question-bank.url}",
        configuration = QuestionBankFeignConfig.class
)
public interface QuestionBankClient {

    /** Selection-time candidate pool. See question-selection-design.md §10. */
    @PostMapping(
            value = "/questions/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<QuestionFilterResponse> filter(@RequestBody QuestionFilterRequest request);

    /**
     * Frozen snapshot of the question with all evaluator-relevant fields
     * (text, audioKey, competency/expectedSignals for behavioral; domain/
     * keyConcepts/depthExpected for core; title/description/testCases for
     * coding). Called by {@code QuestionPicker} after a filter pick so the
     * pinned {@code SessionQuestion.snapshot} carries the full context.
     */
    @GetMapping(
            value = "/questions/{id}/snapshot",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<QuestionSnapshotResponse> getSnapshot(@PathVariable("id") String questionId);

    /** Pre-authored follow-up lookup. Both query params optional. */
    @GetMapping(
            value = "/questions/{id}/follow-ups",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<List<FollowUpResponse>> getFollowUps(
            @PathVariable("id") String parentQuestionId,
            @RequestParam(value = "probesTargetKind", required = false) String probesTargetKind,
            @RequestParam(value = "probesTargetValue", required = false) String probesTargetValue);

    /**
     * Internal counter bump. Called once after the orchestrator pins a
     * question to a session so the next filter prefers cold questions.
     */
    @PostMapping(
            value = "/internal/questions/{id}/mark-asked",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<MarkAskedResponse> markAsked(@PathVariable("id") String questionId);
}
