package com.mockwise.interview.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Mirror of judge-service's {@code JudgeResultEvent} (published to the
 * {@code submission-judged} topic). {@code submissionId} is the
 * interview-service {@code answer.id} we sent down as the judge submission
 * id; {@code verdict} is the aggregate (e.g. {@code "AC"} / {@code "WA"} /
 * {@code "CE"} …), {@code results} is one row per test case.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} so judge-service can
 * add fields without a coordinated deploy. UUIDs are read as strings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmissionJudgedEvent(
        String submissionId,
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
