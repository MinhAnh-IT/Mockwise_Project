package com.mockwise.practice.dto.response;

import java.util.List;

/**
 * A ranked leaderboard slice for one time window. {@code entries} is the top
 * page; {@code me} is the caller's own standing (may sit outside the page);
 * {@code totalParticipants} is everyone with ≥1 solved problem in the window.
 */
public record LeaderboardResponse(
        String window,
        long totalParticipants,
        List<Entry> entries,
        Me me
) {

    /** One ranked row. Score = Σ difficulty weight (Easy 1, Medium 3, Hard 5). */
    public record Entry(
            int rank,
            String userId,
            String fullName,
            long solved,
            long score,
            long easy,
            long medium,
            long hard
    ) {}

    /** The caller's standing — {@code topPercent} is the rounded percentile (1..100). */
    public record Me(
            int rank,
            long solved,
            long score,
            int topPercent
    ) {}
}
