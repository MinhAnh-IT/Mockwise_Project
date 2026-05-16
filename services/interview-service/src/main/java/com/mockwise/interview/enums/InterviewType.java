package com.mockwise.interview.enums;

/**
 * The kind of interview a session runs. Drives blueprint lookup and the
 * COMPETENCY / DOMAIN ratio inside the topic list.
 *
 * <ul>
 *   <li>{@link #BEHAVIORAL} — competency-only.</li>
 *   <li>{@link #CORE} — domain-only (technical conceptual).</li>
 *   <li>{@link #CODING} — LeetCode-style live coding. Non-adaptive: a
 *       fixed list of LIVE_CODING questions is pinned from the blueprint
 *       at /start (no topic matrix, no planner, no follow-ups).</li>
 * </ul>
 */
public enum InterviewType {
    BEHAVIORAL,
    CORE,
    CODING
}
