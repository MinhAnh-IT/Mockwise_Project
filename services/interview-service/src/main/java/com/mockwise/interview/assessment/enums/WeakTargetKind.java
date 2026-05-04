package com.mockwise.interview.assessment.enums;

/**
 * The kind of gap a {@code WeakTarget} represents. Names are sent verbatim
 * to {@code POST /follow-up/generate} and matched against
 * {@code question_follow_up.probes_target_kind} in question-bank, so they
 * are part of the cross-service contract.
 *
 * <ul>
 *   <li>{@link #SIGNAL} — an expected_signal that came back detected=false
 *       (BEHAVIORAL).</li>
 *   <li>{@link #CONCEPT} — a key_concept that was missing or wrong (CORE).</li>
 *   <li>{@link #MISCONCEPTION} — a specific factually-wrong claim (CORE).</li>
 *   <li>{@link #RED_FLAG} — a behavioral red flag like vague_action or
 *       blame_shifting (BEHAVIORAL), or a code issue like wrong_complexity
 *       (LIVE_CODING).</li>
 * </ul>
 */
public enum WeakTargetKind {
    SIGNAL,
    CONCEPT,
    MISCONCEPTION,
    RED_FLAG
}
