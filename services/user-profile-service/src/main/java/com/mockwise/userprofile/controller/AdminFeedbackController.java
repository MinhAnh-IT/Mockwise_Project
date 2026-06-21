package com.mockwise.userprofile.controller;

import com.core.apiresponse.pagination.PageResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.dto.request.FeedbackStatusUpdateRequest;
import com.mockwise.userprofile.dto.response.FeedbackResponse;
import com.mockwise.userprofile.dto.response.FeedbackStatsResponse;
import com.mockwise.userprofile.entity.FeedbackCategory;
import com.mockwise.userprofile.entity.FeedbackStatus;
import com.mockwise.userprofile.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only feedback inbox. The {@code /admin/} path segment makes the gateway
 * enforce ROLE_ADMIN, and SecurityConfig also restricts {@code /admin/**}.
 */
@RestController
@RequestMapping("/admin/feedbacks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminFeedbackController {

    FeedbackService feedbackService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) FeedbackCategory category,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String keyword) {
        Page<FeedbackResponse> result = feedbackService.search(
                status, category, rating, keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PageResponse<FeedbackResponse> pageResponse = PageResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<ApiResponse<FeedbackStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.getStats()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<FeedbackResponse>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody FeedbackStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.updateStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        feedbackService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
