package com.mockwise.practice.dto.response;

import java.util.List;

/**
 * Community insights for the leaderboard page: {@code trending} = problems with
 * the most distinct solvers this week; {@code hardest} = lowest global accept
 * ratio (over graded SUBMITs, past a minimum-submission threshold).
 */
public record CommunityResponse(
        List<Trending> trending,
        List<Hardest> hardest
) {

    public record Trending(
            String problemId,
            String title,
            String difficulty,
            long solvers
    ) {}

    public record Hardest(
            String problemId,
            String title,
            String difficulty,
            long total,
            long accepted,
            double acceptanceRate
    ) {}
}
