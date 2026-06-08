package com.mockwise.interview.repository;

import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository
        extends JpaRepository<InterviewSession, UUID>, JpaSpecificationExecutor<InterviewSession> {

    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Paginated variant for the history list — sort lives on the Pageable. */
    Page<InterviewSession> findByUserId(String userId, Pageable pageable);

    List<InterviewSession> findByStatus(SessionStatus status);

    boolean existsByBlueprintId(UUID blueprintId);

    /**
     * Pessimistic-write lock on a session row. Used by
     * {@link com.mockwise.interview.service.SessionFinalizerService} so the
     * "are all answers terminal? then stage one outbox event" check is
     * serialized — without this, a /finish call racing the last answer's
     * evaluation-completed could double-stage the session-evaluation
     * request.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id")
    Optional<InterviewSession> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Reaper discovery — ids of IN_PROGRESS sessions already past their
     * time budget ({@code started_at + time_budget_minutes}).
     *
     * <p>Native + scalar projection <em>on purpose</em>: it must NOT
     * hydrate the {@link InterviewSession} entity. A legacy row whose
     * {@code interview_type} value is no longer in the Java enum (e.g. a
     * retired {@code 'MIXED'}) makes Hibernate throw
     * {@code No enum constant ... InterviewType.MIXED} while mapping the
     * result set — and because that happens at list-materialisation time,
     * before any per-row guard, it would abort the entire deadline sweep
     * on every tick (the exact defect that left coding sessions stuck
     * IN_PROGRESS forever). Returning bare ids keeps the safety net
     * independent of the enum.
     */
    @Query(value = """
            SELECT CAST(id AS varchar)
              FROM interview_session
             WHERE status = 'IN_PROGRESS'
               AND started_at IS NOT NULL
               AND now() > started_at + (time_budget_minutes * interval '1 minute')
            """, nativeQuery = true)
    List<String> findOverBudgetInProgressIds();

    /**
     * Reaper bulk-finalizer — flips every over-budget IN_PROGRESS session
     * to COMPLETED in a single statement. Native for the same reason as
     * {@link #findOverBudgetInProgressIds()} (no enum hydration), which is
     * also why it can clear legacy unmappable rows that the per-entity
     * path could never finalize. {@code updated_at} is set explicitly
     * because a bulk update bypasses the {@code @UpdateTimestamp} hook.
     *
     * @return number of sessions flipped to COMPLETED
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE interview_session
               SET status = 'COMPLETED', finished_at = now(), updated_at = now()
             WHERE status = 'IN_PROGRESS'
               AND started_at IS NOT NULL
               AND now() > started_at + (time_budget_minutes * interval '1 minute')
            """, nativeQuery = true)
    int completeOverBudgetInProgressSessions();

    // ── Admin stats (native + scalar on purpose) ────────────────────────────
    // Aggregating over the raw columns avoids hydrating InterviewSession, so a
    // legacy row with an interview_type no longer in the Java enum can't abort
    // the whole stats query (same reasoning as the reaper queries above).

    /** {@code [status, count]} rows across all sessions. */
    @Query(value = "SELECT status, COUNT(*) FROM interview_session GROUP BY status", nativeQuery = true)
    List<Object[]> countGroupedByStatus();

    /** {@code [interview_type, count]} rows; interview_type may be null. */
    @Query(value = "SELECT interview_type, COUNT(*) FROM interview_session GROUP BY interview_type", nativeQuery = true)
    List<Object[]> countGroupedByType();

    /** Mean final score over scored sessions, or null when none have a score. */
    @Query(value = "SELECT AVG(final_score) FROM interview_session WHERE final_score IS NOT NULL", nativeQuery = true)
    Double averageFinalScore();
}
