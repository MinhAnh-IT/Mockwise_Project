package com.mockwise.interview.enums;

/**
 * The kind of demonstrated competence a {@code StrongTarget} represents.
 * Sent to {@code POST /follow-up/generate} so the AI does not re-probe
 * skills the candidate already showed.
 */
public enum StrongTargetKind {
    SIGNAL,
    CONCEPT
}
