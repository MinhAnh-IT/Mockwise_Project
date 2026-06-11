package com.mockwise.practice.service;

import com.mockwise.practice.dto.response.UserStats;
import com.mockwise.practice.enums.ProblemStatus;
import com.mockwise.practice.enums.SubmissionMode;
import com.mockwise.practice.repository.PracticeProblemStatusRepository;
import com.mockwise.practice.repository.PracticeSubmissionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes per-user practice stats on read from {@code practice_problem_status}
 * and {@code practice_submission}. No rollup table — the volumes here are small
 * (one row per attempted problem; submissions are user-scoped), so an on-read
 * aggregate stays simple and always consistent.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeStatsService {

    private static final String VERDICT_ACCEPTED = "AC";

    PracticeProblemStatusRepository statusRepo;
    PracticeSubmissionRepository submissionRepo;

    @Transactional(readOnly = true)
    public UserStats getStats(String userId) {
        long solved = statusRepo.countByUserIdAndStatus(userId, ProblemStatus.SOLVED);
        long attemptedOnly = statusRepo.countByUserIdAndStatus(userId, ProblemStatus.ATTEMPTED);
        // Every problem with a status row has been attempted (solved ones too).
        long attemptedTotal = solved + attemptedOnly;

        Map<String, Long> byDifficulty = new LinkedHashMap<>();
        byDifficulty.put("EASY", 0L);
        byDifficulty.put("MEDIUM", 0L);
        byDifficulty.put("HARD", 0L);
        for (Object[] row : statusRepo.countSolvedByDifficulty(userId)) {
            String diff = row[0] == null ? "UNKNOWN" : String.valueOf(row[0]).toUpperCase();
            long count = ((Number) row[1]).longValue();
            byDifficulty.merge(diff, count, Long::sum);
        }

        long submitTotal = submissionRepo.countByUserIdAndMode(userId, SubmissionMode.SUBMIT);
        long submitAccepted = submissionRepo.countByUserIdAndModeAndVerdict(
                userId, SubmissionMode.SUBMIT, VERDICT_ACCEPTED);
        Double acceptanceRate = submitTotal == 0 ? null : (double) submitAccepted / submitTotal;

        List<LocalDate> acceptedDays = submissionRepo.findAcceptedSubmitDates(userId);
        int currentStreak = currentStreak(acceptedDays);
        int longestStreak = longestStreak(acceptedDays);

        return new UserStats(
                solved, byDifficulty, attemptedTotal, acceptanceRate, currentStreak, longestStreak);
    }

    /** Consecutive days up to today (or yesterday) with ≥1 accepted submit. */
    private int currentStreak(List<LocalDate> days) {
        if (days == null || days.isEmpty()) return 0;
        Set<LocalDate> set = new TreeSet<>(days);
        LocalDate today = LocalDate.now();
        LocalDate cursor;
        if (set.contains(today)) {
            cursor = today;
        } else if (set.contains(today.minusDays(1))) {
            cursor = today.minusDays(1);
        } else {
            return 0;
        }
        int streak = 0;
        while (set.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Longest run of consecutive accepted-submit days over all history. */
    private int longestStreak(List<LocalDate> days) {
        if (days == null || days.isEmpty()) return 0;
        TreeSet<LocalDate> set = new TreeSet<>(days);
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : set) { // ascending
            if (prev != null && d.equals(prev.plusDays(1))) {
                run++;
            } else {
                run = 1;
            }
            longest = Math.max(longest, run);
            prev = d;
        }
        return longest;
    }
}
