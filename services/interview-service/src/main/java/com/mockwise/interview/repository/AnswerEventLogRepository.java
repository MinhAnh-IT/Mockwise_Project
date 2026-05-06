package com.mockwise.interview.repository;

import com.mockwise.interview.entity.AnswerEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerEventLogRepository extends JpaRepository<AnswerEventLog, Long> {

    List<AnswerEventLog> findByAnswerIdOrderByOccurredAtAsc(UUID answerId);
}
