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

    /** Distinct calendar days (desc) on which the user had ≥1 ACCEPTED submit — backs streaks. */
    @Query(value = """
            SELECT DISTINCT CAST(created_at AS DATE) AS d
            FROM practice_submission
            WHERE user_id = :userId AND mode = 'SUBMIT' AND verdict = 'ACCEPTED'
            ORDER BY d DESC
            """, nativeQuery = true)
    List<java.time.LocalDate> findAcceptedSubmitDates(@Param("userId") String userId);
}
