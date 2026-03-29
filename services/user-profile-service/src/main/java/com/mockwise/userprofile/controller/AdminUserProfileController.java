package com.mockwise.userprofile.controller;

import com.core.apiresponse.pagination.PageResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.dto.request.UserProfileUpdateRequest;
import com.mockwise.userprofile.dto.response.AdminUserProfileResponse;
import com.mockwise.userprofile.dto.response.ProfileStatsResponse;
import com.mockwise.userprofile.dto.response.UserProfileResponse;
import com.mockwise.userprofile.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/profiles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserProfileController {

    UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserProfileResponse>>> listProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String trackId,
            @RequestParam(required = false) String levelId,
            @RequestParam(required = false) String keyword) {
        Page<AdminUserProfileResponse> result = userProfileService.searchProfilesForAdmin(
                trackId, levelId, keyword, PageRequest.of(page, size));
        PageResponse<AdminUserProfileResponse> pageResponse = PageResponse.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@PathVariable String userId) {
        UserProfileResponse result = userProfileService.getProfileById(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @PathVariable String userId,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        UserProfileResponse result = userProfileService.updateProfileAsAdmin(userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/stats/overview")
    public ResponseEntity<ApiResponse<ProfileStatsResponse>> stats() {
        ProfileStatsResponse stats = userProfileService.getProfileStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
