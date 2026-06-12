package com.mockwise.practice.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.common.security.CurrentUser;
import com.mockwise.practice.dto.response.CommunityResponse;
import com.mockwise.practice.dto.response.LeaderboardResponse;
import com.mockwise.practice.service.LeaderboardService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public (auth-gated) leaderboard + community insights for the practice feature. */
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LeaderboardController {

    LeaderboardService leaderboardService;

    /** Difficulty-weighted ranking for a time window (ALL | WEEK | MONTH), with my standing. */
    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> leaderboard(
            @RequestParam(defaultValue = "ALL") String window,
            @RequestParam(defaultValue = "50") int limit) {
        String userId = CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(
                leaderboardService.leaderboard(userId, window, limit)));
    }

    /** Trending (most solvers this week) + hardest (lowest accept ratio) problems. */
    @GetMapping("/community")
    public ResponseEntity<ApiResponse<CommunityResponse>> community() {
        CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(leaderboardService.community()));
    }
}
