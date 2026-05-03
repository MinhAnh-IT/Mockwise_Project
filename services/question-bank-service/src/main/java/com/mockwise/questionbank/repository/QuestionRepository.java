package com.mockwise.questionbank.repository;

import com.mockwise.questionbank.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, String> {

    /**
     * Atomic increment + timestamp bump for the selector tie-breaker. Done
     * as a UPDATE to avoid the load-modify-save round-trip and to keep the
     * counter accurate even under concurrent session starts.
     */
    @Modifying
    @Query(
        value = "UPDATE questions SET ask_count = ask_count + 1, last_asked_at = NOW() WHERE id = :id",
        nativeQuery = true
    )
    int markAsked(@Param("id") String id);

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
