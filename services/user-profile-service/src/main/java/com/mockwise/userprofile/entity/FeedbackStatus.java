package com.mockwise.userprofile.entity;

/**
 * Triage state of a feedback item. NEW until an admin looks at it; REVIEWED
 * while being worked through; RESOLVED once handled.
 */
public enum FeedbackStatus {
    NEW,
    REVIEWED,
    RESOLVED
}
