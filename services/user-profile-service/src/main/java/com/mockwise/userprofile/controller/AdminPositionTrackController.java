package com.mockwise.userprofile.controller;

import com.core.apiresponse.response.ApiListResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.dto.request.PositionTrackCreateRequest;
import com.mockwise.userprofile.dto.request.PositionTrackUpdateRequest;
import com.mockwise.userprofile.dto.response.PositionTrackResponse;
import com.mockwise.userprofile.service.PositionTrackService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/position-tracks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminPositionTrackController {

    PositionTrackService trackService;

    @PostMapping
    public ResponseEntity<ApiResponse<PositionTrackResponse>> create(
            @Valid @RequestBody PositionTrackCreateRequest request) {
        PositionTrackResponse response = trackService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionTrackResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody PositionTrackUpdateRequest request) {
        PositionTrackResponse response = trackService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        trackService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PositionTrackResponse>> getById(@PathVariable String id) {
        PositionTrackResponse response = trackService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ApiListResponse<PositionTrackResponse>>> getAll() {
        List<PositionTrackResponse> items = trackService.getAll();
        return ResponseEntity.ok(ApiResponse.success(ApiListResponse.of(items)));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<PositionTrackResponse>> toggleActive(@PathVariable String id) {
        PositionTrackResponse response = trackService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<PositionTrackResponse>> disable(@PathVariable String id) {
        PositionTrackResponse response = trackService.disable(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<PositionTrackResponse>> enable(@PathVariable String id) {
        PositionTrackResponse response = trackService.enable(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
