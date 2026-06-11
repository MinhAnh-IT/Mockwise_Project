package com.mockwise.practice.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.common.security.CurrentUser;
import com.mockwise.practice.dto.response.UserStats;
import com.mockwise.practice.service.PracticeStatsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** My aggregate practice stats (solved counts, acceptance rate, streaks). */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeStatsController {

    PracticeStatsService statsService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserStats>> myStats() {
        String userId = CurrentUser.requireUserId();
        return ResponseEntity.ok(ApiResponse.success(statsService.getStats(userId)));
    }
}
