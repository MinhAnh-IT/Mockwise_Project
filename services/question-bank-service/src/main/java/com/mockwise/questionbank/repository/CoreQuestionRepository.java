package com.mockwise.questionbank.repository;

import com.mockwise.questionbank.entity.CoreQuestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
                  AND (:q          IS NULL OR cq.text ILIKE '%' || :q || '%')
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
                  AND (:q          IS NULL OR cq.text ILIKE '%' || :q || '%')
                """,
        nativeQuery = true
    )
    Page<CoreQuestion> findAllWithFilters(
            @Param("domain")      String domain,
            @Param("targetRole")  String targetRole,
            @Param("difficulty")  String difficulty,
            @Param("status")      String status,
            @Param("tags")        String tags,
            @Param("q")           String q,
            Pageable pageable
    );

    /**
     * Selection-time filter for {@code POST /questions/filter}. See
     * {@link BehavioralQuestionRepository#findCandidates} for the contract;
     * {@code targetRole} matches any element of the question's target_roles
     * array so a single role token can hit a multi-role question.
     */
    @Query(
        value = """
                SELECT cq.* FROM core_questions cq
                JOIN questions q ON q.id = cq.id
                WHERE q.status = 'ACTIVE'
                  AND (:domain      IS NULL OR cq.domain = :domain)
                  AND (:targetRole  IS NULL OR CAST(:targetRole AS text) = ANY(cq.target_roles))
                  AND (:difficulty  IS NULL OR q.difficulty = :difficulty)
                  AND (:requireOpener = FALSE OR cq.is_opener = TRUE)
                  AND (:tags         IS NULL OR q.tags && CAST(:tags AS text[]))
                  AND NOT (q.id = ANY(CAST(:excludeIds AS text[])))
                ORDER BY q.ask_count ASC, q.created_at DESC
                LIMIT :limit
                """,
        nativeQuery = true
    )
    List<CoreQuestion> findCandidates(
            @Param("domain")        String domain,
            @Param("targetRole")    String targetRole,
            @Param("difficulty")    String difficulty,
            @Param("requireOpener") boolean requireOpener,
            @Param("tags")          String tags,
            @Param("excludeIds")    String excludeIds,
            @Param("limit")         int limit
    );
}
