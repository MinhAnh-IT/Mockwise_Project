package com.mockwise.interview.entity.enums;

/**
 * Question difficulty. Mirrors the question-bank {@code Difficulty} enum so
 * filter requests round-trip without translation.
 */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD;

    /** Returns the difficulty one notch harder, or {@code this} if already HARD. */
    public Difficulty upgrade() {
        return switch (this) {
            case EASY -> MEDIUM;
            case MEDIUM, HARD -> HARD;
        };
    }

    /** Returns the difficulty one notch easier, or {@code this} if already EASY. */
    public Difficulty downgrade() {
        return switch (this) {
            case HARD -> MEDIUM;
            case MEDIUM, EASY -> EASY;
        };
    }
}
