package com.mockwise.questionbank.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.questionbank.dto.request.QuestionFilterRequest;
import com.mockwise.questionbank.dto.response.*;
import com.mockwise.questionbank.service.QuestionSelectionService;
import com.mockwise.questionbank.service.QuestionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionController {

    QuestionService questionService;
    QuestionSelectionService selectionService;

    // ── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> health() {
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Read single ───────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getById(id)));
    }

    // ── Downstream payloads ───────────────────────────────────────────────────

    @GetMapping("/{id}/for-ai")
    public ResponseEntity<ApiResponse<ForAiResponse>> getForAi(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getForAi(id)));
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/snapshot")
    public ResponseEntity<ApiResponse<QuestionSnapshotResponse>> getSnapshot(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getSnapshot(id)));
    }

    // ── Audio key (read-only) ─────────────────────────────────────────────────

    @GetMapping("/{id}/audio-key")
    public ResponseEntity<ApiResponse<String>> getAudioKey(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getAudioKey(id)));
    }

    // ── Selection-time queries (called by interview-service) ─────────────────

    @PostMapping("/filter")
    public ResponseEntity<ApiResponse<QuestionFilterResponse>> filter(
            @Valid @RequestBody QuestionFilterRequest request) {
        return ResponseEntity.ok(ApiResponse.success(selectionService.filter(request)));
    }

    /**
     * Pre-authored follow-up lookup. Both query params optional: when both
     * are provided we return the exact-match follow-up (or empty); when
     * absent we return all follow-ups for the parent so admin tooling can
     * inspect the catalog.
     */
    @GetMapping("/{id}/follow-ups")
    public ResponseEntity<ApiResponse<List<FollowUpResponse>>> getFollowUps(
            @PathVariable String id,
            @RequestParam(required = false) String probesTargetKind,
            @RequestParam(required = false) String probesTargetValue) {
        return ResponseEntity.ok(ApiResponse.success(
                selectionService.findFollowUps(id, probesTargetKind, probesTargetValue)));
    }
}
