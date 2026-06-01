package com.mockwise.questionbank.enums;

/**
 * Behavioral interview competencies.
 *
 * <p>The primary taxonomy is <b>Amazon's 16 Leadership Principles</b> — behavioral
 * blueprints are authored against these (see
 * interview-service/docs/blueprint-catalog-and-question-bank-spec.md). Stored as
 * a STRING in {@code behavioral_questions.competency} (VARCHAR(64) after
 * question-bank migration V5), and referenced verbatim as the {@code topicValue}
 * of a COMPETENCY blueprint topic — so these names are a cross-service contract
 * and must not be renamed without a coordinated data migration.
 *
 * <p>The trailing {@code LEGACY} block keeps the pre-LP generic competencies
 * alive so questions already seeded against them still load. New blueprints
 * should prefer the Leadership Principles above.
 */
public enum Competency {

    // ── Amazon Leadership Principles ──────────────────────────────────────────
    CUSTOMER_OBSESSION,
    OWNERSHIP,
    INVENT_AND_SIMPLIFY,
    ARE_RIGHT_A_LOT,
    LEARN_AND_BE_CURIOUS,
    HIRE_AND_DEVELOP_THE_BEST,
    INSIST_ON_HIGHEST_STANDARDS,
    THINK_BIG,
    BIAS_FOR_ACTION,
    FRUGALITY,
    EARN_TRUST,
    DIVE_DEEP,
    HAVE_BACKBONE_DISAGREE_AND_COMMIT,
    DELIVER_RESULTS,
    STRIVE_TO_BE_EARTHS_BEST_EMPLOYER,
    SUCCESS_AND_SCALE_BROAD_RESPONSIBILITY,

    // ── Legacy generic competencies (pre-LP) — kept for backward compatibility ─
    CONFLICT_RESOLUTION,
    LEADERSHIP,
    TEAMWORK,
    FAILURE,
    GROWTH,
    COMMUNICATION,
    PRIORITIZATION
}
