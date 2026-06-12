package com.mockwise.practice.service;

import com.mockwise.practice.entity.PracticeSubmission;
import com.mockwise.practice.entity.PracticeSubmissionCase;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory holder for an ephemeral RUN trial. RUN results are never persisted —
 * only graded SUBMITs reach the DB — so the {@link PracticeSubmission} and its
 * per-case rows here are transient POJOs mutated in place as the verdict lands.
 */
@Getter
public class RunResult {

    private final PracticeSubmission submission;
    private final long cachedAtMillis = System.currentTimeMillis();

    @Setter
    private List<PracticeSubmissionCase> cases = new ArrayList<>();

    public RunResult(PracticeSubmission submission) {
        this.submission = submission;
    }
}
