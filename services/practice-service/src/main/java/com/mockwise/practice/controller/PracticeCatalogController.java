package com.mockwise.practice.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.common.security.CurrentUser;
import com.mockwise.practice.dto.response.CodingProblemView;
import com.mockwise.practice.dto.response.PageResponse;
import com.mockwise.practice.dto.response.ProblemSummary;
import com.mockwise.practice.service.PracticeCatalogService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User-facing practice catalog. Mounted under the service context-path
 * {@code /api/v1/practice}, so these resolve to {@code /api/v1/practice/problems}.
 */
@Slf4j
@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeCatalogController {

    PracticeCatalogService catalogService;

    /** Browse ACTIVE coding problems. All filters optional. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProblemSummary>>> list(
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(
                catalogService.listProblems(userId, difficulty, tags, q, page, size)));
    }

    /** Open one problem into the workspace (visible sample test cases only). */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CodingProblemView>> getById(@PathVariable String id) {
        String userId = CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(catalogService.getProblem(userId, id)));
    }
}
