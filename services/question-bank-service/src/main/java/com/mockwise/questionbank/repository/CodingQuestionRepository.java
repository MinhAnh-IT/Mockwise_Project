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
                  AND (:q          IS NULL OR cq.title ILIKE '%' || :q || '%'
                                           OR cq.description ILIKE '%' || :q || '%')
                ORDER BY q.created_at DESC
                """,
        countQuery = """
                SELECT COUNT(*) FROM coding_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:status     IS NULL OR q.status     = :status)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                  AND (:q          IS NULL OR cq.title ILIKE '%' || :q || '%'
                                           OR cq.description ILIKE '%' || :q || '%')
                """,
        nativeQuery = true
    )
    Page<CodingQuestion> findAllWithFilters(
            @Param("difficulty") String difficulty,
            @Param("status")     String status,
            @Param("tags")       String tags,
            @Param("q")          String q,
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

    /**
     * Practice catalog browse: ACTIVE coding problems only, with optional
     * difficulty, tag-overlap, and case-insensitive title search ({@code q}).
     * Ordered newest-first. Backs {@code GET /internal/coding-problems} which
     * practice-service proxies to the LeetCode-style problem list.
     */
    @Query(
        value = """
                SELECT cq.* FROM coding_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE q.status = 'ACTIVE'
                  AND (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                  AND (:q          IS NULL OR cq.title ILIKE '%' || :q || '%')
                ORDER BY q.created_at DESC
                """,
        countQuery = """
                SELECT COUNT(*) FROM coding_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE q.status = 'ACTIVE'
                  AND (:difficulty IS NULL OR q.difficulty = :difficulty)
                  AND (:tags       IS NULL OR q.tags && CAST(:tags AS text[]))
                  AND (:q          IS NULL OR cq.title ILIKE '%' || :q || '%')
                """,
        nativeQuery = true
    )
    Page<CodingQuestion> findActiveProblems(
            @Param("difficulty") String difficulty,
            @Param("tags")       String tags,
            @Param("q")          String q,
            Pageable pageable
    );

    /**
     * Subset of {@code ids} that are still ACTIVE coding problems. Lets
     * practice-service drop community/leaderboard rows whose denormalized
     * {@code question_id} outlived a deleted or re-seeded problem (which would
     * otherwise render a phantom entry linking to a 404 detail page).
     */
    @Query(
        value = """
                SELECT cq.id FROM coding_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE q.status = 'ACTIVE' AND cq.id IN (:ids)
                """,
        nativeQuery = true
    )
    java.util.List<String> findActiveIdsIn(@Param("ids") java.util.Collection<String> ids);
}
