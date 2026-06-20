package com.mockwise.practice.dto.response;

import java.util.List;

/**
 * Community insights for the leaderboard page: {@code trending} = problems with
 * the most distinct participants this week (anyone who submitted, solved or not);
 * {@code hardest} = lowest per-user
 * solve-through rate (distinct solvers / distinct attempters), restricted to
 * problems with enough attempters and a solve rate below the "hard" ceiling.
 */
public record CommunityResponse(
        List<Trending> trending,
        List<Hardest> hardest
) {

    public record Trending(
            String problemId,
            String title,
            String difficulty,
            long participants
    ) {}

    public record Hardest(
            String problemId,
            String title,
            String difficulty,
            long attempters,
            long solvers,
            double solveRate
    ) {}
}
