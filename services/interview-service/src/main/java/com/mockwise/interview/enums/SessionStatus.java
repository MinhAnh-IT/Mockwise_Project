package com.mockwise.interview.enums;

/**
 * Lifecycle states for an interview_session row.
 *
 * <p>Transitions allowed:
 * <pre>
 *   CREATED      → IN_PROGRESS | CANCELLED
 *   IN_PROGRESS  → COMPLETED | CANCELLED
 *   COMPLETED    → SCORED          (after every answer reaches SCORED/FAILED)
 * </pre>
 *
 * <p>SCORED is terminal. CANCELLED is terminal — background processing of
 * already-submitted answers may continue, but no new answers are accepted.
 */
public enum SessionStatus {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    SCORED
}
