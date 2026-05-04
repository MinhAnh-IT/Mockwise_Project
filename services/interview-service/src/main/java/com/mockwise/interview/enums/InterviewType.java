package com.mockwise.interview.enums;

/**
 * The kind of interview a session runs. Drives blueprint lookup and the
 * COMPETENCY / DOMAIN ratio inside the topic list.
 *
 * <ul>
 *   <li>{@link #BEHAVIORAL} — competency-only.</li>
 *   <li>{@link #CORE} — domain-only (technical conceptual).</li>
 *   <li>{@link #MIXED} — both, interleaved per blueprint.</li>
 * </ul>
 */
public enum InterviewType {
    BEHAVIORAL,
    CORE,
    MIXED
}
