package com.mockwise.questionbank.repository;

import com.mockwise.questionbank.entity.CoreQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoreQuestionRepository extends JpaRepository<CoreQuestion, String> {

    @Query(
        value = """
                SELECT cq.* FROM core_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE (:domain     IS NULL OR cq.domain = :domain)
                  AND (:targetRole IS NULL OR CAST(:targetRole AS text) = ANY(cq.target_roles))
                  AND (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:status     IS NULL OR q.status     = :status)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                ORDER BY q.created_at DESC
                """,
        countQuery = """
                SELECT COUNT(*) FROM core_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE (:domain     IS NULL OR cq.domain = :domain)
                  AND (:targetRole IS NULL OR CAST(:targetRole AS text) = ANY(cq.target_roles))
                  AND (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:status     IS NULL OR q.status     = :status)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                """,
        nativeQuery = true
    )
    Page<CoreQuestion> findAllWithFilters(
            @Param("domain")      String domain,
            @Param("targetRole")  String targetRole,
            @Param("difficulty")  String difficulty,
            @Param("status")      String status,
            @Param("tags")        String tags,
            Pageable pageable
    );
}
