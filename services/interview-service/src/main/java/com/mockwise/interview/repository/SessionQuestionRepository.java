package com.mockwise.interview.repository;

import com.mockwise.interview.entity.SessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, UUID> {

    List<SessionQuestion> findBySessionIdOrderBySequenceAsc(UUID sessionId);

    /**
     * Used by the planner to compute the next sequence number when pinning
     * a new question. {@code MAX(sequence)} is fine here — the planner
     * holds the session row in a transaction so concurrent inserts are
     * not possible.
     */
    Optional<SessionQuestion> findFirstBySessionIdOrderBySequenceDesc(UUID sessionId);

    long countBySessionId(UUID sessionId);
}
