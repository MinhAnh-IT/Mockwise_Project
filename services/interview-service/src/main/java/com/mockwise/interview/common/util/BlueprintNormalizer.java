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

    private static final Map<String, String> TRACK_ALIASES = Map.of(
            "backend",   "BACKEND",
            "back-end",  "BACKEND",
            "frontend",  "FRONTEND",
            "front-end", "FRONTEND",
            "fullstack", "FULLSTACK",
            "full-stack","FULLSTACK",
            "mobile",    "MOBILE",
            "devops",    "DEVOPS",
            "qa",        "QA",
            "data-engineer", "DATA_ENGINEER"
    );

    private static final Map<String, String> LEVEL_ALIASES = Map.of(
            "junior", "junior",
            "intern", "junior",
            "fresher","junior",
            "mid",    "mid",
            "middle", "mid",
            "senior", "senior",
            "lead",   "senior",
            "staff",  "senior"
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
