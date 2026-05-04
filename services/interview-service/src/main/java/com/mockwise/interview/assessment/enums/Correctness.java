package com.mockwise.interview.assessment.enums;

/**
 * Whether the candidate's claims were factually right.
 *
 * <ul>
 *   <li>{@link #WRONG} — fundamental misunderstanding (drives Case A1 in the
 *       decision tree: move on, do not probe).</li>
 *   <li>{@link #MIXED} — partly right, partly off (drives Case A2 / Case C
 *       depending on completeness/depth).</li>
 *   <li>{@link #CORRECT} — substantively accurate (Case D candidate).</li>
 * </ul>
 */
public enum Correctness {
    WRONG,
    MIXED,
    CORRECT
}
