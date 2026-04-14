package com.mockwise.questionbank.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.questionbank.dto.response.*;
import com.mockwise.questionbank.service.QuestionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionController {

    QuestionService questionService;

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
}
