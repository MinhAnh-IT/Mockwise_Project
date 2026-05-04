package com.mockwise.interview.enums;

/**
 * Per-topic evidence level inside a session. See question-selection-design.md §1.
 *
 * <ul>
 *   <li>{@link #NOT_TESTED} — not asked yet. Not in scoring.</li>
 *   <li>{@link #PROBING} — at least one question asked, no verdict yet.</li>
 *   <li>{@link #STRONG} — score ≥ 7 with STRONG signals. Counts at full weight.</li>
 *   <li>{@link #ADEQUATE} — basic competence demonstrated.</li>
 *   <li>{@link #PARTIAL} — gaps remain after follow-up quota exhausted; reduced weight.</li>
 *   <li>{@link #WEAK} — wrong on the fundamentals. Reduced weight, drags score.</li>
 *   <li>{@link #UNKNOWN} — candidate opted out (NO_ANSWER). Not counted at all
 *       — report flags topic as "not assessed".</li>
 * </ul>
 *
 * <p>The WEAK / UNKNOWN distinction is load-bearing: an honest "I don't know"
 * must not be penalized like a wrong answer.
 */
public enum TopicStatus {
    NOT_TESTED,
    PROBING,
    STRONG,
    ADEQUATE,
    PARTIAL,
    WEAK,
    UNKNOWN
}
