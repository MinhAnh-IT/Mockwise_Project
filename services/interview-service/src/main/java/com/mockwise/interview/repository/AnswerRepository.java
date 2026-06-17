package com.mockwise.interview.repository;

import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.enums.AnswerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    List<Answer> findBySessionId(UUID sessionId);

    Optional<Answer> findBySessionQuestionId(UUID sessionQuestionId);

    /** One answer per session_question is the invariant; submit() guards on this. */
    boolean existsBySessionQuestionId(UUID sessionQuestionId);

    Optional<Answer> findByStorageObjectId(UUID storageObjectId);

    /**
     * Used by the cron that watches for stuck answers (EVALUATING > 15 min →
     * republish, after 3 republishes → mark FAILED). Returns by status so
     * the caller can apply its own age cut-off.
     */
    List<Answer> findByStatus(AnswerStatus status);

    /**
     * Counts answers in a session whose status is in {@code statuses}.
     * Used by {@link com.mockwise.interview.service.SessionFinalizerService}
     * to detect "all answers terminal" before staging the overall-review
     * request.
     */
    long countBySessionIdAndStatusIn(UUID sessionId, Collection<AnswerStatus> statuses);

    /**
     * Counts answers in a session whose status is NOT in {@code statuses}.
     * Used by {@link com.mockwise.interview.service.SessionFinalizerService}
     * to detect "no answer is still mid-flight" — the gate for staging the
     * overall-review request. Unlike comparing against the pinned-question
     * count, this ignores questions that were pinned but never answered (user
     * ended early / time up), which would otherwise hold the gate open forever.
     */
    long countBySessionIdAndStatusNotIn(UUID sessionId, Collection<AnswerStatus> statuses);
}
