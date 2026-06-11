package com.mockwise.practice.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Mirror of judge-service's {@code JudgeResultEvent} on the
 * {@code submission-judged} topic. {@code origin} lets this service skip
 * verdicts belonging to interview; {@code submissionId} maps back to a
 * {@code PracticeSubmission}. Unknown fields are ignored so judge can evolve.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmissionJudgedEvent(
        String submissionId,
        String origin,
        String verdict,
        List<CaseResult> results
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaseResult(
            String testCaseId,
            /** AC | WA | TLE | MLE | RE | CE | PENDING (judge TaskStatus). */
            String status,
            String stdout,
            String stderr,
            Integer runtimeMs,
            Integer memoryKb
    ) {}
}
