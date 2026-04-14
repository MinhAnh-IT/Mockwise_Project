package com.mockwise.questionbank.repository;

import com.mockwise.questionbank.entity.BehavioralQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BehavioralQuestionRepository extends JpaRepository<BehavioralQuestion, String> {

    @Query(
        value = """
                SELECT bq.* FROM behavioral_questions bq
                JOIN questions q ON q.id = bq.id
                WHERE (:competency IS NULL OR bq.competency = :competency)
                  AND (:difficulty  IS NULL OR q.difficulty  = :difficulty)
                  AND (:status      IS NULL OR q.status      = :status)
                  AND (:tags        IS NULL OR q.tags && CAST(:tags AS text[]))
                ORDER BY q.created_at DESC
                """,
        countQuery = """
                SELECT COUNT(*) FROM behavioral_questions bq
                JOIN questions q ON q.id = bq.id
                WHERE (:competency IS NULL OR bq.competency = :competency)
                  AND (:difficulty  IS NULL OR q.difficulty  = :difficulty)
                  AND (:status      IS NULL OR q.status      = :status)
                  AND (:tags        IS NULL OR q.tags && CAST(:tags AS text[]))
                """,
        nativeQuery = true
    )
    Page<BehavioralQuestion> findAllWithFilters(
            @Param("competency") String competency,
            @Param("difficulty") String difficulty,
            @Param("status")     String status,
            @Param("tags")       String tags,
            Pageable pageable
    );
}
