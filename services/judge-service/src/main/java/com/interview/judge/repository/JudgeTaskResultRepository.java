package com.interview.judge.repository;

import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.JudgeTaskResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JudgeTaskResultRepository extends JpaRepository<JudgeTaskResult, UUID> {

    List<JudgeTaskResult> findByJobOrderByOrderIndex(JudgeJob job);

    /**
     * Locking read used by the Judge0 callback handler so that two callbacks for
     * the SAME task (Judge0 can deliver a callback more than once) serialize and
     * only the first one — seeing status PENDING — advances {@code doneCases}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM JudgeTaskResult t WHERE t.id = :id")
    Optional<JudgeTaskResult> findByIdForUpdate(@Param("id") UUID id);
}
