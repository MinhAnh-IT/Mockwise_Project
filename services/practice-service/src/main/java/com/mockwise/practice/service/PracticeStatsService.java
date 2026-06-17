package com.mockwise.practice.service;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.client.questionbank.QuestionBankClient;
import com.mockwise.practice.client.questionbank.dto.QbPage;
import com.mockwise.practice.client.questionbank.dto.QbProblemSummary;
import com.mockwise.practice.dto.response.UserStats;
import com.mockwise.practice.enums.ProblemStatus;
import com.mockwise.practice.enums.SubmissionMode;
import com.mockwise.practice.repository.PracticeProblemStatusRepository;
import com.mockwise.practice.repository.PracticeSubmissionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
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
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PracticeStatsService {

    private static final String VERDICT_ACCEPTED = "AC";
    private static final List<String> DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");

    PracticeProblemStatusRepository statusRepo;
    PracticeSubmissionRepository submissionRepo;
    QuestionBankClient questionBank;

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

        Map<String, Long> totalByDifficulty = catalogTotalsByDifficulty();
        long totalProblems = totalByDifficulty.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Long> solvedByLanguage = solvedByLanguage(userId);
        Map<String, Long> solvedByTag = solvedByTag(userId);

        // Native query returns java.sql.Date (MySQL DATE); convert to LocalDate
        // here since Spring has no java.sql.Date -> LocalDate collection converter.
        List<LocalDate> acceptedDays = submissionRepo.findAcceptedSubmitDates(userId).stream()
                .map(java.sql.Date::toLocalDate)
                .toList();
        int currentStreak = currentStreak(acceptedDays);
        int longestStreak = longestStreak(acceptedDays);

        return new UserStats(
                solved, totalProblems, byDifficulty, totalByDifficulty,
                solvedByLanguage, solvedByTag,
                attemptedTotal, acceptanceRate, currentStreak, longestStreak);
    }

    /** Distinct solved problems per language, highest first. */
    private Map<String, Long> solvedByLanguage(String userId) {
        Map<String, Long> byLang = new LinkedHashMap<>();
        submissionRepo.countSolvedByLanguage(userId, VERDICT_ACCEPTED).stream()
                .sorted((a, b) -> Long.compare(((Number) b[1]).longValue(), ((Number) a[1]).longValue()))
                .forEach(row -> {
                    if (row[0] != null) byLang.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
                });
        return byLang;
    }

    /**
     * Distinct solved problems per question-bank tag, highest first. Tags are
     * denormalized onto problem-status on solve, so this stays a single local read.
     */
    private Map<String, Long> solvedByTag(String userId) {
        Map<String, Long> tally = new HashMap<>();
        for (var status : statusRepo.findByUserIdAndStatus(userId, ProblemStatus.SOLVED)) {
            if (status.getTags() == null) continue;
            for (String tag : status.getTags()) {
                if (tag != null && !tag.isBlank()) tally.merge(tag, 1L, Long::sum);
            }
        }
        return tally.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }

    /**
     * Global catalog size per difficulty, read from question-bank (one count
     * query per difficulty via {@code size=1} + {@code totalElements}). Best-effort:
     * if question-bank is unavailable the totals fall back to 0 so the rest of the
     * stats payload still resolves.
     */
    private Map<String, Long> catalogTotalsByDifficulty() {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (String difficulty : DIFFICULTIES) {
            totals.put(difficulty, countCatalog(difficulty));
        }
        return totals;
    }

    private long countCatalog(String difficulty) {
        try {
            ApiResponse<QbPage<QbProblemSummary>> res =
                    questionBank.listProblems(difficulty, null, null, 0, 1);
            if (res == null || !res.isSuccess() || res.getData() == null) {
                return 0L;
            }
            return res.getData().totalElements();
        } catch (Exception ex) {
            log.warn("Catalog count for difficulty={} failed: {}", difficulty, ex.getMessage());
            return 0L;
        }
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
