package com.interview.judge.repository;

import com.interview.judge.entity.JudgeJob;
import com.interview.judge.entity.JudgeTaskResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JudgeTaskResultRepository extends JpaRepository<JudgeTaskResult, UUID> {

    List<JudgeTaskResult> findByJobOrderByOrderIndex(JudgeJob job);
}
