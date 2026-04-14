package com.mockwise.questionbank.repository;

import com.mockwise.questionbank.entity.CodingQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodingQuestionRepository extends JpaRepository<CodingQuestion, String> {

    @Query(
        value = """
                SELECT cq.* FROM coding_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:status     IS NULL OR q.status     = :status)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                ORDER BY q.created_at DESC
                """,
        countQuery = """
                SELECT COUNT(*) FROM coding_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:status     IS NULL OR q.status     = :status)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                """,
        nativeQuery = true
    )
    Page<CodingQuestion> findAllWithFilters(
            @Param("difficulty") String difficulty,
            @Param("status")     String status,
            @Param("tags")       String tags,
            Pageable pageable
    );
}
