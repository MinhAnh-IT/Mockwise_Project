package com.mockwise.iam.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.iam.dto.response.UserResponse;
import com.mockwise.iam.service.AdminUserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin account moderation endpoints. Gated to ROLE_ADMIN by the gateway (any
 * path containing {@code /admin/} requires admin). {@code X-User-Id} is the
 * acting admin, injected by the gateway after JWT introspection.
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserController {

    AdminUserService adminUserService;

    @PatchMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<UserResponse>> block(
            @RequestHeader("X-User-Id") String actingUserId,
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.blockUser(actingUserId, userId)));
    }

    @PatchMapping("/{userId}/unblock")
    public ResponseEntity<ApiResponse<UserResponse>> unblock(
            @RequestHeader("X-User-Id") String actingUserId,
            @PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.unblockUser(actingUserId, userId)));
    }
}
