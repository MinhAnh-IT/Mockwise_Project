package com.mockwise.interview.common.util;

import java.util.Locale;
import java.util.Map;

/**
 * Lookup utilities for translating user-profile-service strings into the
 * canonical forms blueprints are keyed by.
 *
 * <p>User-profile lets admins configure tracks freely (e.g. "Backend",
 * "Front-End", "Mobile / iOS"). Blueprints are keyed by an exact token
 * ("BACKEND", "FRONTEND", ...) — drift between the two is the most
 * likely cause of a session failing to start.
 *
 * <p>Strategy: an explicit alias table for the cases we know about, plus
 * an uppercase-and-strip fallback for everything else. The fallback is
 * forgiving on purpose — a misspelled track name in user-profile should
 * fail the lookup loudly with "no blueprint for X" rather than silently
 * pick the wrong one.
 */
public final class BlueprintNormalizer {

    private BlueprintNormalizer() {}

    // Keys are the lowercased+trimmed user-profile track names. Values are the
    // canonical blueprint tokens. Must stay in sync with the actual
    // position_tracks rows in user-profile (see blueprint-catalog-and-question-bank-spec.md
    // §1.2): "QA/Tester", "AI/ML Engineer", "Data Engineer", "BA", etc.
    private static final Map<String, String> TRACK_ALIASES = Map.ofEntries(
            Map.entry("backend",            "BACKEND"),
            Map.entry("back-end",           "BACKEND"),
            Map.entry("frontend",           "FRONTEND"),
            Map.entry("front-end",          "FRONTEND"),
            Map.entry("fullstack",          "FULLSTACK"),
            Map.entry("full-stack",         "FULLSTACK"),
            Map.entry("mobile",             "MOBILE"),
            Map.entry("devops",             "DEVOPS"),
            Map.entry("qa",                 "QA"),
            Map.entry("qa/tester",          "QA"),
            Map.entry("tester",             "QA"),
            Map.entry("data engineer",      "DATA_ENGINEER"),
            Map.entry("data-engineer",      "DATA_ENGINEER"),
            Map.entry("ai/ml engineer",     "AI_ML"),
            Map.entry("ai/ml",              "AI_ML"),
            Map.entry("ml engineer",        "AI_ML"),
            Map.entry("ba",                 "BA"),
            Map.entry("business analyst",   "BA")
    );

    // Maps every seniority label (incl. management/leadership tiers) onto the
    // three blueprint levels. Director/Manager/Principal/Staff/Lead all fold to
    // senior — interview difficulty doesn't model an "above senior" tier.
    private static final Map<String, String> LEVEL_ALIASES = Map.ofEntries(
            Map.entry("junior",    "junior"),
            Map.entry("intern",    "junior"),
            Map.entry("fresher",   "junior"),
            Map.entry("mid",       "mid"),
            Map.entry("middle",    "mid"),
            Map.entry("senior",    "senior"),
            Map.entry("lead",      "senior"),
            Map.entry("staff",     "senior"),
            Map.entry("principal", "senior"),
            Map.entry("manager",   "senior"),
            Map.entry("director",  "senior")
    );

    /** "Backend" → "BACKEND"; unknown tracks fall through uppercased + non-letters stripped. */
    public static String normalizeRole(String trackName) {
        if (trackName == null || trackName.isBlank()) return "";
        String key = trackName.trim().toLowerCase(Locale.ROOT);
        String alias = TRACK_ALIASES.get(key);
        if (alias != null) return alias;
        // Fallback: uppercase, replace whitespace/dash with underscore, drop other punctuation.
        return key.replaceAll("[\\s-]+", "_").replaceAll("[^a-z0-9_]", "").toUpperCase(Locale.ROOT);
    }

    /** "Mid" → "mid"; unknown levels fall through lowercased. */
    public static String normalizeLevel(String levelName) {
        if (levelName == null || levelName.isBlank()) return "";
        String key = levelName.trim().toLowerCase(Locale.ROOT);
        String alias = LEVEL_ALIASES.get(key);
        return alias != null ? alias : key;
    }
}
