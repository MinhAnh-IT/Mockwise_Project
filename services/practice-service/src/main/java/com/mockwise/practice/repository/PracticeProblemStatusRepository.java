package com.mockwise.practice.repository;

import com.mockwise.practice.entity.PracticeProblemStatus;
import com.mockwise.practice.enums.ProblemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PracticeProblemStatusRepository
        extends JpaRepository<PracticeProblemStatus, PracticeProblemStatus.Key> {

    Optional<PracticeProblemStatus> findByUserIdAndQuestionId(String userId, String questionId);

    /** Status rows for a set of problems — backs {@code myStatus} on the list view. */
    List<PracticeProblemStatus> findByUserIdAndQuestionIdIn(String userId, List<String> questionIds);

    long countByUserIdAndStatus(String userId, ProblemStatus status);

    /** Solved/attempted rows for a user — backs per-tag progress (tags read in-app). */
    List<PracticeProblemStatus> findByUserIdAndStatus(String userId, ProblemStatus status);

    /** Solved counts grouped by denormalized difficulty for the stats page. */
    @Query("""
            SELECT s.difficulty, COUNT(s)
            FROM PracticeProblemStatus s
            WHERE s.userId = :userId AND s.status = com.mockwise.practice.enums.ProblemStatus.SOLVED
            GROUP BY s.difficulty
            """)
    List<Object[]> countSolvedByDifficulty(@Param("userId") String userId);
}
