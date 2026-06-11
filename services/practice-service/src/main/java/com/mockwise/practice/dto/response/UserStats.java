package com.mockwise.practice.dto.response;

import java.util.Map;

/**
 * Aggregate practice stats for one user.
 * {@code acceptanceRate} = accepted SUBMITs / total SUBMITs (0..1, null if none).
 * {@code solvedByDifficulty} keys are difficulty names (EASY/MEDIUM/HARD).
 */
public record UserStats(
        long solvedTotal,
        Map<String, Long> solvedByDifficulty,
        long attemptedTotal,
        Double acceptanceRate,
        int currentStreakDays,
        int longestStreakDays
) {}
