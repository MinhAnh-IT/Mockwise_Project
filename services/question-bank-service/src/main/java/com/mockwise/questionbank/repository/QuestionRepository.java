package com.mockwise.questionbank.repository;

import com.mockwise.questionbank.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, String> {

    @Query(
        value = """
                SELECT * FROM questions q
                WHERE (:type     IS NULL OR q.type       = :type)
                  AND (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:status   IS NULL OR q.status     = :status)
                  AND (:tags     IS NULL OR q.tags && CAST(:tags AS text[]))
                """,
        countQuery = """
                SELECT COUNT(*) FROM questions q
                WHERE (:type     IS NULL OR q.type       = :type)
                  AND (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:status   IS NULL OR q.status     = :status)
                  AND (:tags     IS NULL OR q.tags && CAST(:tags AS text[]))
                """,
        nativeQuery = true
    )
    Page<Question> findAllWithFilters(
            @Param("type")       String type,
            @Param("difficulty") String difficulty,
            @Param("status")     String status,
            @Param("tags")       String tags,
            Pageable pageable
    );
}
