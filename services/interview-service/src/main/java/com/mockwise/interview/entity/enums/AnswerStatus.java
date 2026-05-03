package com.mockwise.interview.entity.enums;

/**
 * Lifecycle states for an answer row. See interview-service-design.md §3.
 *
 * <p>Transitions:
 * <pre>
 *   SUBMITTED  → PROCESSING                       (event published to tts-stt or judge)
 *   PROCESSING → READY | FAILED                   (transcript-ready or judged result)
 *   READY      → EVALUATING                       (evaluation-requested published)
 *   EVALUATING → SCORED | FAILED                  (evaluation-completed or timeout)
 * </pre>
 *
 * <p>SCORED and FAILED are terminal in the happy path. Operator-triggered
 * replay can re-enter PROCESSING from FAILED.
 */
public enum AnswerStatus {
    SUBMITTED,
    PROCESSING,
    READY,
    EVALUATING,
    SCORED,
    FAILED
}
