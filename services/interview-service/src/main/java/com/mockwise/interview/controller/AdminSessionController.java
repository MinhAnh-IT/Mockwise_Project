package com.mockwise.interview.controller;

import com.core.apiresponse.response.ApiListResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.dto.admin.response.AdminSessionResponse;
import com.mockwise.interview.dto.admin.response.AdminSessionStatsResponse;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.service.admin.AdminSessionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * Admin-only, read-only oversight of interview sessions. The gateway enforces
 * ROLE_ADMIN for any /admin/ path. Path prefix from the servlet context-path:
 * /api/v1/interviews + /admin/sessions.
 *
 * <p>Exposes a filtered list and aggregate stats ONLY — no get-by-id and no
 * mutation, so an admin can never inspect a session's answers or edit it.
 */
@RestController
@RequestMapping("/admin/sessions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminSessionController {

    AdminSessionService service;

    @GetMapping
    public ResponseEntity<ApiResponse<ApiListResponse<AdminSessionResponse>>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) InterviewType interviewType,
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminSessionResponse> p = service.list(
                userId, status, interviewType, targetRole, level, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(
                ApiListResponse.of(p.getContent(), (int) p.getTotalElements())));
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<ApiResponse<AdminSessionStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success(service.stats()));
    }
}
