package com.mockwise.practice.repository;

import com.mockwise.practice.entity.PracticeSubmission;
import com.mockwise.practice.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PracticeSubmissionRepository extends JpaRepository<PracticeSubmission, String> {

    /** Ownership-scoped lookup — a user only ever sees their own submissions. */
    Optional<PracticeSubmission> findByIdAndUserId(String id, String userId);

    /**
     * History with optional filters. Null filter args are ignored, so the same
     * query backs "all of mine" and any narrowed slice.
     */
    @Query("""
            SELECT s FROM PracticeSubmission s
            WHERE s.userId = :userId
              AND (:questionId IS NULL OR s.questionId = :questionId)
              AND (:mode IS NULL OR s.mode = :mode)
              AND (:verdict IS NULL OR s.verdict = :verdict)
            ORDER BY s.createdAt DESC
            """)
    Page<PracticeSubmission> search(@Param("userId") String userId,
                                    @Param("questionId") String questionId,
                                    @Param("mode") com.mockwise.practice.enums.SubmissionMode mode,
                                    @Param("verdict") String verdict,
                                    Pageable pageable);

    /** Watchdog source: submissions stuck awaiting a verdict past the deadline. */
    List<PracticeSubmission> findByStatusAndCreatedAtBefore(SubmissionStatus status, LocalDateTime before);

    /** SUBMIT acceptance-rate numerator/denominator for the user's stats. */
    long countByUserIdAndMode(String userId, com.mockwise.practice.enums.SubmissionMode mode);

    long countByUserIdAndModeAndVerdict(String userId,
                                        com.mockwise.practice.enums.SubmissionMode mode,
                                        String verdict);

    /**
     * Global acceptance aggregate for a set of problems — one row per question as
     * {@code [questionId, totalGraded, accepted]} over <em>graded</em> SUBMITs
     * (verdict applied; RUN trials and unfinished/failed dispatches excluded).
     * Backs {@code acceptanceRate} on the problem-list view. GROUP BY only emits
     * questions with ≥1 graded submit, so {@code totalGraded} is always ≥ 1.
     */
    @Query("""
            SELECT s.questionId, COUNT(s), SUM(CASE WHEN s.verdict = :accepted THEN 1L ELSE 0L END)
            FROM PracticeSubmission s
            WHERE s.mode = com.mockwise.practice.enums.SubmissionMode.SUBMIT
              AND s.verdict IS NOT NULL
              AND s.questionId IN :questionIds
            GROUP BY s.questionId
            """)
    List<Object[]> aggregateAcceptance(@Param("questionIds") List<String> questionIds,
                                       @Param("accepted") String accepted);

    /**
     * Per-problem roll-up of the user's graded SUBMITs (newest activity first) —
     * backs the LeetCode-style "progress" view. Each row is
     * {@code [questionId, title, difficulty, total, accepted, bestRuntimeMs,
     * firstSolvedAt, lastSubmittedAt]}; the AC-conditioned aggregates are null
     * until the problem is solved.
     */
    @Query("""
            SELECT s.questionId, MAX(s.problemTitle), MAX(s.difficulty),
                   COUNT(s),
                   SUM(CASE WHEN s.verdict = :accepted THEN 1L ELSE 0L END),
                   MIN(CASE WHEN s.verdict = :accepted THEN s.runtimeMs ELSE NULL END),
                   MIN(CASE WHEN s.verdict = :accepted THEN s.createdAt ELSE NULL END),
                   MAX(s.createdAt)
            FROM PracticeSubmission s
            WHERE s.userId = :userId AND s.mode = com.mockwise.practice.enums.SubmissionMode.SUBMIT
            GROUP BY s.questionId
            ORDER BY MAX(s.createdAt) DESC
            """)
    List<Object[]> groupByProblem(@Param("userId") String userId,
                                  @Param("accepted") String accepted);

    /** Distinct solved-problem count per language ({@code [language, count]}) — progress "Languages". */
    @Query("""
            SELECT s.language, COUNT(DISTINCT s.questionId)
            FROM PracticeSubmission s
            WHERE s.userId = :userId
              AND s.mode = com.mockwise.practice.enums.SubmissionMode.SUBMIT
              AND s.verdict = :accepted
            GROUP BY s.language
            """)
    List<Object[]> countSolvedByLanguage(@Param("userId") String userId,
                                         @Param("accepted") String accepted);

    /**
     * Cross-user ranking by difficulty-weighted score (Easy 1, Medium 3, Hard 5)
     * over distinct solved problems since {@code :since}. Each row is
     * {@code [userId, solved, score, easy, medium, hard]}, best first. Pass a far-
     * past {@code since} for the all-time board.
     */
    @Query(value = """
            SELECT t.user_id,
                   COUNT(*)                                                  AS solved,
                   SUM(t.weight)                                             AS score,
                   SUM(CASE WHEN t.difficulty = 'EASY'   THEN 1 ELSE 0 END)  AS easy,
                   SUM(CASE WHEN t.difficulty = 'MEDIUM' THEN 1 ELSE 0 END)  AS medium,
                   SUM(CASE WHEN t.difficulty = 'HARD'   THEN 1 ELSE 0 END)  AS hard
            FROM (
                SELECT DISTINCT s.user_id, s.question_id, s.difficulty,
                       CASE s.difficulty
                            WHEN 'EASY' THEN 1 WHEN 'MEDIUM' THEN 3 WHEN 'HARD' THEN 5 ELSE 1 END AS weight
                FROM practice_submission s
                WHERE s.mode = 'SUBMIT' AND s.verdict = 'AC' AND s.created_at >= :since
            ) t
            GROUP BY t.user_id
            ORDER BY score DESC, solved DESC, t.user_id ASC
            """, nativeQuery = true)
    List<Object[]> leaderboardRanked(@Param("since") java.time.LocalDateTime since);

    /**
     * Problems with the most distinct solvers since {@code :since} (trending).
     * Each row is {@code [questionId, title, difficulty, solvers]}, hottest first.
     */
    @Query(value = """
            SELECT s.question_id, MAX(s.problem_title), MAX(s.difficulty),
                   COUNT(DISTINCT s.user_id) AS solvers
            FROM practice_submission s
            WHERE s.mode = 'SUBMIT' AND s.verdict = 'AC' AND s.created_at >= :since
            GROUP BY s.question_id
            ORDER BY solvers DESC
            """, nativeQuery = true)
    List<Object[]> trendingProblems(@Param("since") java.time.LocalDateTime since);

    /**
     * Lowest global accept ratio over graded SUBMITs, past a minimum-submission
     * threshold. Each row is {@code [questionId, title, difficulty, total, accepted]},
     * hardest first.
     */
    @Query(value = """
            SELECT s.question_id, MAX(s.problem_title), MAX(s.difficulty),
                   COUNT(*) AS total,
                   SUM(CASE WHEN s.verdict = 'AC' THEN 1 ELSE 0 END) AS accepted
            FROM practice_submission s
            WHERE s.mode = 'SUBMIT' AND s.verdict IS NOT NULL
            GROUP BY s.question_id
            HAVING COUNT(*) >= :minSubmissions
            ORDER BY (CAST(SUM(CASE WHEN s.verdict = 'AC' THEN 1 ELSE 0 END) AS double precision) / COUNT(*)) ASC,
                     total DESC
            """, nativeQuery = true)
    List<Object[]> hardestProblems(@Param("minSubmissions") long minSubmissions);

    /** Distinct calendar days (desc) on which the user had ≥1 ACCEPTED submit — backs streaks. */
    @Query(value = """
            SELECT DISTINCT CAST(created_at AS DATE) AS d
            FROM practice_submission
            WHERE user_id = :userId AND mode = 'SUBMIT' AND verdict = 'AC'
            ORDER BY d DESC
            """, nativeQuery = true)
    List<java.time.LocalDate> findAcceptedSubmitDates(@Param("userId") String userId);
}
