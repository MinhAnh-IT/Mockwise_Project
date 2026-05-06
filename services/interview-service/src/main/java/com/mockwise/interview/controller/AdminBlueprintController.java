package com.mockwise.interview.controller;

import com.core.apiresponse.response.ApiListResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.dto.admin.request.BlueprintCreateRequest;
import com.mockwise.interview.dto.admin.request.BlueprintUpdateRequest;
import com.mockwise.interview.dto.admin.request.SetDefaultRequest;
import com.mockwise.interview.dto.admin.response.BlueprintAdminResponse;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.service.admin.BlueprintAdminService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only catalog endpoints for interview blueprints (templates).
 * The api-gateway enforces ROLE_ADMIN for any path containing /admin/, so
 * no @PreAuthorize is needed here. Path prefix from servlet context-path:
 * /api/v1/interviews + /admin/blueprints.
 */
@Slf4j
@RestController
@RequestMapping("/admin/blueprints")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminBlueprintController {

    BlueprintAdminService service;

    @PostMapping
    public ResponseEntity<ApiResponse<BlueprintAdminResponse>> create(
            @RequestBody @Valid BlueprintCreateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(body)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ApiListResponse<BlueprintAdminResponse>>> list(
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) InterviewType interviewType,
            @RequestParam(required = false) Boolean isDefault,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BlueprintAdminResponse> p = service.list(
                targetRole, level, interviewType, isDefault,
                PageRequest.of(page, size, Sort.by("targetRole", "level", "interviewType")));
        return ResponseEntity.ok(ApiResponse.success(
                ApiListResponse.of(p.getContent(), (int) p.getTotalElements())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BlueprintAdminResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BlueprintAdminResponse>> update(
            @PathVariable UUID id,
            @RequestBody @Valid BlueprintUpdateRequest body) {
        return ResponseEntity.ok(ApiResponse.success(service.update(id, body)));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<BlueprintAdminResponse>> setDefault(
            @PathVariable UUID id,
            @RequestBody @Valid SetDefaultRequest body) {
        return ResponseEntity.ok(ApiResponse.success(service.setDefault(id, body.isDefault())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null));
    }
}
