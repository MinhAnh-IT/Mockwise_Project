package com.mockwise.interview.repository;

import com.mockwise.interview.entity.SessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, UUID> {

    List<SessionQuestion> findBySessionIdOrderBySequenceAsc(UUID sessionId);

    /**
     * {@code [sessionId, count]} rows for the given sessions — the number of
     * questions actually pinned to each (follow-ups included). Used by the
     * admin list to show a real "answered / total" instead of the planned
     * {@code question_count}, which is only exact for non-adaptive CODING.
     * Scalar projection, so the {@link SessionQuestion} entity is never
     * hydrated.
     */
    @Query("select sq.sessionId, count(sq) from SessionQuestion sq "
            + "where sq.sessionId in :ids group by sq.sessionId")
    List<Object[]> countGroupedBySessionId(@Param("ids") Collection<UUID> ids);

    /**
     * Used by the planner to compute the next sequence number when pinning
     * a new question. {@code MAX(sequence)} is fine here — the planner
     * holds the session row in a transaction so concurrent inserts are
     * not possible.
     */
    Optional<SessionQuestion> findFirstBySessionIdOrderBySequenceDesc(UUID sessionId);

    long countBySessionId(UUID sessionId);
}
