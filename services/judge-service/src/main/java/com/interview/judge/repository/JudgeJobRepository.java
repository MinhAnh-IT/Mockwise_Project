package com.interview.judge.repository;

import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.enums.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JudgeJobRepository extends JpaRepository<JudgeJob, UUID> {

    Optional<JudgeJob> findBySubmissionId(UUID submissionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM JudgeJob j WHERE j.id = :id")
    Optional<JudgeJob> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Atomically increments doneCases by 1.
     * Returns the number of rows updated (1 on success).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JudgeJob j SET j.doneCases = j.doneCases + 1 WHERE j.id = :id")
    int incrementDoneCases(@Param("id") UUID id);

    /**
     * Transitions job to DONE only if it is currently RUNNING.
     * Returns 1 if the transition succeeded (this caller "wins" finalization), 0 if already DONE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JudgeJob j SET j.status = :done WHERE j.id = :id AND j.status = :running")
    int markDoneIfRunning(@Param("id") UUID id,
                          @Param("running") JobStatus running,
                          @Param("done") JobStatus done);

    // ── Admin monitoring aggregates ──────────────────────────────────────────
    // Read-only rollups over judge_jobs for the admin Judge-monitoring dashboard.

    /** Live backlog: jobs not yet finalized, regardless of when they were created. */
    long countByStatusIn(Collection<JobStatus> statuses);

    /** Job count grouped by lifecycle status within a window. Rows: [JobStatus, Long]. */
    @Query("SELECT j.status, COUNT(j) FROM JudgeJob j WHERE j.createdAt >= :since GROUP BY j.status")
    List<Object[]> countByStatusSince(@Param("since") LocalDateTime since);

    /** Job count grouped by verdict within a window (verdict may be null). Rows: [String, Long]. */
    @Query("SELECT j.verdict, COUNT(j) FROM JudgeJob j WHERE j.createdAt >= :since GROUP BY j.verdict")
    List<Object[]> countByVerdictSince(@Param("since") LocalDateTime since);

    /** (createdAt, finishedAt) pairs for finished jobs in a window — latency computed in Java. */
    @Query("SELECT j.createdAt, j.finishedAt FROM JudgeJob j " +
            "WHERE j.finishedAt IS NOT NULL AND j.createdAt >= :since")
    List<Object[]> finishedTimestampsSince(@Param("since") LocalDateTime since);

    /** Jobs that needed at least one Judge0 resubmit (transient-error retries) in a window. */
    @Query("SELECT COUNT(j) FROM JudgeJob j WHERE j.createdAt >= :since AND j.retryCount > 0")
    long countRetriedSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(MAX(j.retryCount), 0) FROM JudgeJob j WHERE j.createdAt >= :since")
    int maxRetryCountSince(@Param("since") LocalDateTime since);

    /** Paged job search with optional status / verdict filters, newest first. */
    @Query("SELECT j FROM JudgeJob j " +
            "WHERE (:status IS NULL OR j.status = :status) " +
            "AND (:verdict IS NULL OR j.verdict = :verdict) " +
            "ORDER BY j.createdAt DESC")
    Page<JudgeJob> search(@Param("status") JobStatus status,
                          @Param("verdict") String verdict,
                          Pageable pageable);
}
