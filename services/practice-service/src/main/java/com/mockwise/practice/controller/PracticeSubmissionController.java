package com.mockwise.practice.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.common.security.CurrentUser;
import com.mockwise.practice.dto.request.RunSubmitRequest;
import com.mockwise.practice.dto.response.PageResponse;
import com.mockwise.practice.dto.response.ProblemSubmissionGroup;
import com.mockwise.practice.dto.response.SubmissionCreatedResponse;
import com.mockwise.practice.dto.response.SubmissionDetail;
import com.mockwise.practice.dto.response.SubmissionSummary;
import com.mockwise.practice.enums.SubmissionMode;
import com.mockwise.practice.service.PracticeSubmissionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Run / Submit dispatch and submission history. {@code userId} always comes
 * from the gateway-verified token, never the request.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeSubmissionController {

    PracticeSubmissionService submissionService;

    /** Trial run against sample cases. 202 — poll {@code GET /submissions/{id}} for the verdict. */
    @PostMapping("/problems/{id}/run")
    public ResponseEntity<ApiResponse<SubmissionCreatedResponse>> run(
            @PathVariable String id, @Valid @RequestBody RunSubmitRequest req) {
        String userId = CurrentUser.requireUserId();
        SubmissionCreatedResponse res = submissionService.run(userId, id, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(res));
    }

    /** Graded submit against the full case set. 202 — poll for the verdict. */
    @PostMapping("/problems/{id}/submit")
    public ResponseEntity<ApiResponse<SubmissionCreatedResponse>> submit(
            @PathVariable String id, @Valid @RequestBody RunSubmitRequest req) {
        String userId = CurrentUser.requireUserId();
        SubmissionCreatedResponse res = submissionService.submit(userId, id, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(res));
    }

    /** My submission history, newest first. All filters optional. */
    @GetMapping("/submissions")
    public ResponseEntity<ApiResponse<PageResponse<SubmissionSummary>>> list(
            @RequestParam(required = false) String problemId,
            @RequestParam(required = false) SubmissionMode mode,
            @RequestParam(required = false) String verdict,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(
                submissionService.listSubmissions(userId, problemId, mode, verdict, page, size)));
    }

    /** My SUBMITs rolled up per problem (LeetCode "progress" view), newest activity first. */
    @GetMapping("/submissions/grouped")
    public ResponseEntity<ApiResponse<List<ProblemSubmissionGroup>>> grouped() {
        String userId = CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(submissionService.listProblemGroups(userId)));
    }

    /** One submission (source + per-case). Hidden cases expose status only. */
    @GetMapping("/submissions/{id}")
    public ResponseEntity<ApiResponse<SubmissionDetail>> detail(@PathVariable String id) {
        String userId = CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(submissionService.getSubmission(userId, id)));
    }
}
