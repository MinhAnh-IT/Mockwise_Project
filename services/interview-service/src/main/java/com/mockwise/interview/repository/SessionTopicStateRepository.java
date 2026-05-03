package com.mockwise.interview.repository;

import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.entity.SessionTopicStateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionTopicStateRepository
        extends JpaRepository<SessionTopicState, SessionTopicStateId> {

    List<SessionTopicState> findByIdSessionId(UUID sessionId);
}
