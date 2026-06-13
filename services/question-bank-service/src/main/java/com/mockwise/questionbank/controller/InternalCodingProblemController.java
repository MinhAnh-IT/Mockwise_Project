package com.mockwise.questionbank.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.questionbank.dto.response.CodingProblemSummary;
import com.mockwise.questionbank.dto.response.CodingQuestionResponse;
import com.mockwise.questionbank.dto.response.PageResponse;
import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.service.CodingCatalogService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Service-to-service catalog for the practice (LeetCode-style) feature.
 * Consumed only by practice-service, which adds the user-facing concerns
 * (hidden-case stripping, per-user solved status). Mounted under
 * {@code /internal} so the gateway never exposes the full payload — which
 * carries hidden test cases — to browsers.
 *
 * <p>Note: question-bank's SecurityConfig is currently permitAll, so the
 * {@code /internal} prefix is a routing convention enforced at the gateway
 * (same posture as {@link InternalQuestionController}). Tighten when
 * X-Internal-Auth is wired into this service.
 */
@Slf4j
@RestController
@RequestMapping("/internal/coding-problems")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalCodingProblemController {

    CodingCatalogService catalogService;

    /** Paginated browse over ACTIVE coding problems. All filters optional. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CodingProblemSummary>>> list(
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                catalogService.listProblems(difficulty, tags, q, PageRequest.of(page, size))));
    }

    /**
     * Of the supplied {@code ids}, returns those that are still ACTIVE coding
     * problems. Lets practice-service drop community/leaderboard rows whose
     * denormalized {@code question_id} outlived a deleted or re-seeded problem.
     * Declared before {@code /{id}} so the literal path takes precedence.
     */
    @GetMapping("/existing")
    public ResponseEntity<ApiResponse<List<String>>> existing(@RequestParam List<String> ids) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.existingActiveIds(ids)));
    }

    /**
     * Full detail of one ACTIVE coding problem, including hidden test cases.
     * practice-service strips hidden cases before serving a user and uses the
     * full set only to build a Submit run against the judge.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CodingQuestionResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getDetail(id)));
    }
}
