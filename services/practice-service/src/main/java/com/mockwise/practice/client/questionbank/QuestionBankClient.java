package com.mockwise.practice.client.questionbank;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.client.questionbank.config.QuestionBankFeignConfig;
import com.mockwise.practice.client.questionbank.dto.QbCodingDetail;
import com.mockwise.practice.client.questionbank.dto.QbPage;
import com.mockwise.practice.client.questionbank.dto.QbProblemSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Read-only view onto question-bank's internal coding-problem catalog
 * ({@code /internal/coding-problems}). The {@code url} includes question-bank's
 * context-path; {@link QuestionBankFeignConfig} injects {@code X-Internal-Auth}.
 */
@FeignClient(
        name = "question-bank-catalog",
        url = "${external.services.question-bank.url}",
        configuration = QuestionBankFeignConfig.class
)
public interface QuestionBankClient {

    /** Paginated ACTIVE coding-problem browse. All filters optional. */
    @GetMapping(
            value = "/internal/coding-problems",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<QbPage<QbProblemSummary>> listProblems(
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam("page") int page,
            @RequestParam("size") int size);

    /** Full detail of one ACTIVE coding problem (includes hidden test cases). */
    @GetMapping(
            value = "/internal/coding-problems/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<QbCodingDetail> getProblem(@PathVariable("id") String id);

    /** Subset of {@code ids} that are still ACTIVE coding problems. */
    @GetMapping(
            value = "/internal/coding-problems/existing",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<List<String>> existingIds(@RequestParam("ids") List<String> ids);
}
