package com.mockwise.userprofile.controller;

import com.core.apiresponse.response.ApiListResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.dto.request.PositionLevelCreateRequest;
import com.mockwise.userprofile.dto.request.PositionLevelUpdateRequest;
import com.mockwise.userprofile.dto.response.PositionLevelResponse;
import com.mockwise.userprofile.service.PositionLevelService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/position-levels")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminPositionLevelController {

    PositionLevelService levelService;

    @PostMapping
    public ResponseEntity<ApiResponse<PositionLevelResponse>> create(
            @Valid @RequestBody PositionLevelCreateRequest request) {
        PositionLevelResponse response = levelService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionLevelResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody PositionLevelUpdateRequest request) {
        PositionLevelResponse response = levelService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        levelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionLevelResponse>> getById(@PathVariable String id) {
        PositionLevelResponse response = levelService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ApiListResponse<PositionLevelResponse>>> getAll() {
        List<PositionLevelResponse> items = levelService.getAll();
        return ResponseEntity.ok(ApiResponse.success(ApiListResponse.of(items)));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<PositionLevelResponse>> toggleActive(@PathVariable String id) {
        PositionLevelResponse response = levelService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<PositionLevelResponse>> disable(@PathVariable String id) {
        PositionLevelResponse response = levelService.disable(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<PositionLevelResponse>> enable(@PathVariable String id) {
        PositionLevelResponse response = levelService.enable(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
