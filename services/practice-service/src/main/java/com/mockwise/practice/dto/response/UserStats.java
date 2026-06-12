package com.mockwise.practice.dto.response;

import java.util.Map;

/**
 * Aggregate practice stats for one user.
 * {@code acceptanceRate} = accepted SUBMITs / total SUBMITs (0..1, null if none).
 * {@code solvedByDifficulty}/{@code totalByDifficulty} keys are difficulty names
 * (EASY/MEDIUM/HARD); {@code totalProblems} is the global catalog size so the UI
 * can render a "solved / total" progress ring.
 */
public record UserStats(
        long solvedTotal,
        long totalProblems,
        Map<String, Long> solvedByDifficulty,
        Map<String, Long> totalByDifficulty,
        /** Distinct solved problems per language (e.g. python/java) — progress "Languages". */
        Map<String, Long> solvedByLanguage,
        /** Distinct solved problems per question-bank tag — progress "Skills". */
        Map<String, Long> solvedByTag,
        long attemptedTotal,
        Double acceptanceRate,
        int currentStreakDays,
        int longestStreakDays
) {}
