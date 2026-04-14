package com.mockwise.questionbank.controller;

import com.core.apiresponse.response.ApiListResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.questionbank.dto.request.*;
import com.mockwise.questionbank.dto.response.*;
import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.QuestionStatus;
import com.mockwise.questionbank.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminQuestionController {

    QuestionService questionService;

    static final String HEADER_USER_ID = "X-User-Id";

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping("/behavioral")
    public ResponseEntity<ApiResponse<BehavioralQuestionResponse>> createBehavioral(
            @RequestBody @Valid BehavioralQuestionRequest body,
            HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionService.createBehavioral(body, userId)));
    }

    @PostMapping("/core")
    public ResponseEntity<ApiResponse<CoreQuestionResponse>> createCore(
            @RequestBody @Valid CoreQuestionRequest body,
            HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionService.createCore(body, userId)));
    }

    @PostMapping("/coding")
    public ResponseEntity<ApiResponse<CodingQuestionResponse>> createCoding(
            @RequestBody @Valid CodingQuestionRequest body,
            HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(questionService.createCoding(body, userId)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/behavioral/{id}")
    public ResponseEntity<ApiResponse<BehavioralQuestionResponse>> updateBehavioral(
            @PathVariable String id,
            @RequestBody @Valid BehavioralQuestionRequest body) {
        return ResponseEntity.ok(ApiResponse.success(questionService.updateBehavioral(id, body)));
    }

    @PutMapping("/core/{id}")
    public ResponseEntity<ApiResponse<CoreQuestionResponse>> updateCore(
            @PathVariable String id,
            @RequestBody @Valid CoreQuestionRequest body) {
        return ResponseEntity.ok(ApiResponse.success(questionService.updateCore(id, body)));
    }

    @PutMapping("/coding/{id}")
    public ResponseEntity<ApiResponse<CodingQuestionResponse>> updateCoding(
            @PathVariable String id,
            @RequestBody @Valid CodingQuestionRequest body) {
        return ResponseEntity.ok(ApiResponse.success(questionService.updateCoding(id, body)));
    }

    // ── Status & Delete ───────────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String id,
            @RequestBody @Valid StatusUpdateRequest body) {
        questionService.updateStatus(id, body.getStatus());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        questionService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null));
    }

    // ── List (admin only — users access questions only through Interview Service) ────

    @GetMapping("/behavioral")
    public ResponseEntity<ApiListResponse<BehavioralQuestionResponse>> getBehavioral(
            @RequestParam(required = false) String competency,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(questionService.getBehavioral(competency, difficulty, status, tags, pageable));
    }

    @GetMapping("/core")
    public ResponseEntity<ApiListResponse<CoreQuestionResponse>> getCore(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(questionService.getCore(domain, targetRole, difficulty, status, tags, pageable));
    }

    @GetMapping("/coding")
    public ResponseEntity<ApiListResponse<CodingQuestionResponse>> getCoding(
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(questionService.getCoding(difficulty, status, tags, pageable));
    }

    // ── For-judge (contains test cases — admin only) ──────────────────────────

    @GetMapping("/{id}/for-judge")
    public ResponseEntity<ApiResponse<ForJudgeResponse>> getForJudge(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getForJudge(id)));
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/audio")
    public ResponseEntity<ApiResponse<Void>> updateAudioKey(
            @PathVariable String id,
            @RequestBody @Valid AudioKeyRequest body) {
        questionService.updateAudioKey(id, body.getAudioKey());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
