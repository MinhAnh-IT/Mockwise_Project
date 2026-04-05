package com.interview.judge.repository;

import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.enums.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
