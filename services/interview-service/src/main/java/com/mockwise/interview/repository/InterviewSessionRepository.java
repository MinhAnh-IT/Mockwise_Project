package com.mockwise.interview.repository;

import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {

    List<InterviewSession> findByUserIdOrderByCreatedAtDesc(String userId);

    List<InterviewSession> findByStatus(SessionStatus status);
}
