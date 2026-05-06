package com.mockwise.interview.repository;

import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(String userId);

    List<InterviewSession> findByStatus(SessionStatus status);

    boolean existsByBlueprintId(UUID blueprintId);

    /**
     * Pessimistic-write lock on a session row. Used by
     * {@link com.mockwise.interview.service.SessionFinalizerService} so the
     * "are all answers terminal? then stage one outbox event" check is
     * serialized — without this, a /finish call racing the last answer's
     * evaluation-completed could double-stage the session-evaluation
     * request.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id")
    Optional<InterviewSession> findByIdForUpdate(@Param("id") UUID id);
}
