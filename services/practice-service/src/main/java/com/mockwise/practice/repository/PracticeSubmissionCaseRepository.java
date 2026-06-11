package com.mockwise.practice.repository;

import com.mockwise.practice.entity.PracticeSubmissionCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeSubmissionCaseRepository extends JpaRepository<PracticeSubmissionCase, String> {

    /** Per-case rows for one submission, in test-case order. */
    List<PracticeSubmissionCase> findBySubmissionIdOrderByOrderIndex(String submissionId);
}
