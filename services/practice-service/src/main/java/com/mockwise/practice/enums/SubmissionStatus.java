package com.mockwise.practice.enums;

/**
 * Lifecycle of a {@code PracticeSubmission}. Distinct from the judge
 * {@code verdict}: a WRONG_ANSWER run is still {@link #DONE} — {@link #FAILED}
 * means an infrastructure fault (could not dispatch, watchdog timeout).
 */
public enum SubmissionStatus {
    PENDING,   // row created, code-submission not published yet
    JUDGING,   // published, awaiting verdict
    DONE,      // verdict applied (any verdict value)
    FAILED     // infrastructure failure — never reached the judge / timed out
}
