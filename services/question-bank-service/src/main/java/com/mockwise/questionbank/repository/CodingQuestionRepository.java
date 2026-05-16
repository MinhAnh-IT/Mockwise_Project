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

    /**
     * Selection-time filter for {@code POST /questions/filter} with
     * {@code type=LIVE_CODING}. Mirrors the behavioral/core candidate
     * queries: ACTIVE only, optional difficulty + tag overlap,
     * {@code excludeIds} as a {@code text[]} literal ({@code "{}"} disables
     * it), ordered ask_count ASC so cold problems surface first.
     *
     * <p>No competency / domain / opener filtering — coding questions carry
     * none of those; interview-service picks N distinct ids from the pool.
     */
    @Query(
        value = """
                SELECT cq.* FROM coding_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE q.status = 'ACTIVE'
                  AND (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                  AND NOT (q.id = ANY(CAST(:excludeIds AS text[])))
                ORDER BY q.ask_count ASC, q.created_at DESC
                LIMIT :limit
                """,
        nativeQuery = true
    )
    java.util.List<CodingQuestion> findCandidates(
            @Param("difficulty") String difficulty,
            @Param("tags")       String tags,
            @Param("excludeIds") String excludeIds,
            @Param("limit")      int limit
    );
}
