package com.mockwise.interview.enums;

/**
 * How important a blueprint topic is. The planner sorts candidate next-topics
 * by importance DESC, so HIGH-importance topics get tested before LOW even
 * when both are still NOT_TESTED.
 */
public enum Importance {
    HIGH,
    MED,
    LOW
}
