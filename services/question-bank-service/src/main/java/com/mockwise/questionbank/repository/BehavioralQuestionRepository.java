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
                  AND (:q           IS NULL OR bq.text ILIKE '%' || :q || '%')
                ORDER BY q.created_at DESC
                """,
        countQuery = """
                SELECT COUNT(*) FROM behavioral_questions bq
                JOIN questions q ON q.id = bq.id
                WHERE (:competency IS NULL OR bq.competency = :competency)
                  AND (:difficulty  IS NULL OR q.difficulty  = :difficulty)
                  AND (:status      IS NULL OR q.status      = :status)
                  AND (:tags        IS NULL OR q.tags && CAST(:tags AS text[]))
                  AND (:q           IS NULL OR bq.text ILIKE '%' || :q || '%')
                """,
        nativeQuery = true
    )
    Page<BehavioralQuestion> findAllWithFilters(
            @Param("competency") String competency,
            @Param("difficulty") String difficulty,
            @Param("status")     String status,
            @Param("tags")       String tags,
            @Param("q")          String q,
            Pageable pageable
    );

    /**
     * Selection-time filter for {@code POST /questions/filter}. Returns up
     * to {@code limit} candidates ordered by ask_count ASC so cold questions
     * surface before popular ones — interview-service applies any further
     * scoring (opener bonus, tag overlap) on the returned set.
     *
     * <p>{@code excludeIds} is a {@code text[]} literal (e.g. {@code "{q1,q2}"});
     * empty array {@code "{}"} disables the exclusion.
     */
    @Query(
        value = """
                SELECT bq.* FROM behavioral_questions bq
                JOIN questions q ON q.id = bq.id
                WHERE q.status = 'ACTIVE'
                  AND (:competency  IS NULL OR bq.competency = :competency)
                  AND (:difficulty  IS NULL OR q.difficulty  = :difficulty)
                  AND (:requireOpener = FALSE OR bq.is_opener = TRUE)
                  AND (:tags         IS NULL OR q.tags && CAST(:tags AS text[]))
                  AND NOT (q.id = ANY(CAST(:excludeIds AS text[])))
                ORDER BY q.ask_count ASC, q.created_at DESC
                LIMIT :limit
                """,
        nativeQuery = true
    )
    java.util.List<BehavioralQuestion> findCandidates(
            @Param("competency")    String competency,
            @Param("difficulty")    String difficulty,
            @Param("requireOpener") boolean requireOpener,
            @Param("tags")          String tags,
            @Param("excludeIds")    String excludeIds,
            @Param("limit")         int limit
    );
}
