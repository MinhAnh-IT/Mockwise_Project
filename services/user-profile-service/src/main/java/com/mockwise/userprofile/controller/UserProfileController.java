package com.mockwise.userprofile.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.common.security.CustomUserDetails;
import com.mockwise.userprofile.dto.request.UserProfileRequest;
import com.mockwise.userprofile.dto.request.UserProfileUpdateRequest;
import com.mockwise.userprofile.dto.response.UserProfileResponse;
import com.mockwise.userprofile.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/profiles")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileController {

    UserProfileService service;

    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> createProfile(
            @PathVariable String userId,
            @RequestBody @Valid UserProfileRequest profileRequest) {
        UserProfileResponse result = service.createProfile(userId, profileRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProfileResponse result = service.getProfileById(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Get user profile by userId — used by other internal services (e.g., Interview Service).
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfileByUserId(
            @PathVariable String userId) {
        UserProfileResponse result = service.getProfileById(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @PathVariable String userId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid UserProfileUpdateRequest profileRequest) {
        UserProfileResponse result = service.updateProfile(userDetails.getUserId(), userId, profileRequest);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfilePut(
            @PathVariable String userId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid UserProfileUpdateRequest profileRequest) {
        UserProfileResponse result = service.updateProfile(userDetails.getUserId(), userId, profileRequest);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
