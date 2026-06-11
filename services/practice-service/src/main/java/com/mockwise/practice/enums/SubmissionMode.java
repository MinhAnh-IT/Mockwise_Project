package com.mockwise.practice.enums;

/** Whether a submission was a sample-only trial run or a graded submit. */
public enum SubmissionMode {
    /** Trial against sample (non-hidden) cases only — never changes problem status. */
    RUN,
    /** Graded against the full case set (incl. hidden) — drives ATTEMPTED/SOLVED. */
    SUBMIT
}
