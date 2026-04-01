package com.interview.judge.repository;

import com.interview.judge.entity.JudgeJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
