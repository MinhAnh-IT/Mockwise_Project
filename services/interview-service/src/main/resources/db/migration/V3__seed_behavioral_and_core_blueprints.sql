-- Interview Service — seed BEHAVIORAL and CORE blueprints
--
-- V2 only seeded MIXED blueprints, so /start with interviewType=BEHAVIORAL or
-- CORE returned BLUEPRINT_NOT_FOUND (4042) for every user. This migration adds
-- the matching BEHAVIORAL + CORE rows for the four (role, level) combos V2
-- already covers, so no user falls into a hole when they pick those types.
--
-- BEHAVIORAL blueprints are pure COMPETENCY topics (no DOMAIN), CORE are pure
-- DOMAIN (no COMPETENCY) — the loader's pickFirstTopic prefers COMPETENCY
-- topics for non-CORE types, which is naturally satisfied here.
--
-- Topic enum values must match question-bank's Competency / Domain enums
-- byte-for-byte (services/question-bank-service/.../enums/{Competency,Domain}.java).
--
-- Inserts use WHERE NOT EXISTS instead of ON CONFLICT because the unique key
-- on (target_role, level, interview_type) is a *partial* index (WHERE
-- is_default = TRUE), which ON CONFLICT cannot target. The guard makes this
-- migration idempotent — safe to re-run after a manual prod backfill.

-- ─── BACKEND-junior-BEHAVIORAL ───────────────────────────────────────────────
-- Light warm-up set for early-career candidates: ownership, teamwork,
-- communication, growth. Smaller budget mirrors the junior-MIXED shape.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'BACKEND', 'junior', 'BEHAVIORAL',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",     "importance":"MED", "targetDifficulty":"EASY",  "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"TEAMWORK",      "importance":"HIGH","targetDifficulty":"EASY",  "orderHint":2},
        {"kind":"COMPETENCY","topicValue":"COMMUNICATION", "importance":"HIGH","targetDifficulty":"EASY",  "orderHint":3},
        {"kind":"COMPETENCY","topicValue":"GROWTH",        "importance":"MED", "targetDifficulty":"EASY",  "orderHint":4}
    ]'::jsonb,
    5, 1, 2, 25, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='BACKEND' AND level='junior' AND interview_type='BEHAVIORAL' AND is_default=TRUE
);

-- ─── BACKEND-mid-BEHAVIORAL ──────────────────────────────────────────────────
-- Mid candidates get probed on conflict, leadership and failure stories on
-- top of the warm-up — the higher-stakes signals expected at this level.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'BACKEND', 'mid', 'BEHAVIORAL',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",           "importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"CONFLICT_RESOLUTION", "importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":2},
        {"kind":"COMPETENCY","topicValue":"COMMUNICATION",       "importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":3},
        {"kind":"COMPETENCY","topicValue":"LEADERSHIP",          "importance":"MED", "targetDifficulty":"MEDIUM", "orderHint":4},
        {"kind":"COMPETENCY","topicValue":"FAILURE",             "importance":"MED", "targetDifficulty":"MEDIUM", "orderHint":5}
    ]'::jsonb,
    6, 2, 3, 30, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='BACKEND' AND level='mid' AND interview_type='BEHAVIORAL' AND is_default=TRUE
);

-- ─── FRONTEND-mid-BEHAVIORAL ─────────────────────────────────────────────────
-- Frontend roles weight communication / prioritization heavier than backend
-- (cross-functional with design + product), so swap conflict for those two.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'FRONTEND', 'mid', 'BEHAVIORAL',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",      "importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"COMMUNICATION",  "importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":2},
        {"kind":"COMPETENCY","topicValue":"TEAMWORK",       "importance":"HIGH","targetDifficulty":"EASY",   "orderHint":3},
        {"kind":"COMPETENCY","topicValue":"PRIORITIZATION", "importance":"MED", "targetDifficulty":"MEDIUM", "orderHint":4},
        {"kind":"COMPETENCY","topicValue":"GROWTH",         "importance":"MED", "targetDifficulty":"MEDIUM", "orderHint":5}
    ]'::jsonb,
    6, 2, 3, 30, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='FRONTEND' AND level='mid' AND interview_type='BEHAVIORAL' AND is_default=TRUE
);

-- ─── FULLSTACK-mid-BEHAVIORAL ────────────────────────────────────────────────
-- Fullstack mid mirrors backend-mid (conflict + leadership) but adds
-- prioritization to reflect the wider surface area they own day-to-day.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'FULLSTACK', 'mid', 'BEHAVIORAL',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",           "importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"CONFLICT_RESOLUTION", "importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":2},
        {"kind":"COMPETENCY","topicValue":"COMMUNICATION",       "importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":3},
        {"kind":"COMPETENCY","topicValue":"PRIORITIZATION",      "importance":"MED", "targetDifficulty":"MEDIUM", "orderHint":4},
        {"kind":"COMPETENCY","topicValue":"LEADERSHIP",          "importance":"MED", "targetDifficulty":"MEDIUM", "orderHint":5}
    ]'::jsonb,
    6, 2, 3, 30, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='FULLSTACK' AND level='mid' AND interview_type='BEHAVIORAL' AND is_default=TRUE
);

-- ─── BACKEND-junior-CORE ─────────────────────────────────────────────────────
-- Foundational backend technical sweep at EASY: DB basics + language depth +
-- one architectural concept (patterns) + testing hygiene.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'BACKEND', 'junior', 'CORE',
    '[
        {"kind":"DOMAIN","topicValue":"DATABASE",          "importance":"HIGH","targetDifficulty":"EASY","orderHint":1},
        {"kind":"DOMAIN","topicValue":"LANGUAGE_SPECIFIC", "importance":"HIGH","targetDifficulty":"EASY","orderHint":2},
        {"kind":"DOMAIN","topicValue":"DESIGN_PATTERN",    "importance":"MED", "targetDifficulty":"EASY","orderHint":3},
        {"kind":"DOMAIN","topicValue":"TESTING",           "importance":"MED", "targetDifficulty":"EASY","orderHint":4}
    ]'::jsonb,
    5, 1, 2, 25, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='BACKEND' AND level='junior' AND interview_type='CORE' AND is_default=TRUE
);

-- ─── BACKEND-mid-CORE ────────────────────────────────────────────────────────
-- The canonical mid-backend technical interview. Expanded over MIXED — no
-- behavioral budget to spend, so we add LANGUAGE_SPECIFIC + TESTING.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'BACKEND', 'mid', 'CORE',
    '[
        {"kind":"DOMAIN","topicValue":"DATABASE",          "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":1},
        {"kind":"DOMAIN","topicValue":"SYSTEM_DESIGN",     "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":2},
        {"kind":"DOMAIN","topicValue":"LANGUAGE_SPECIFIC", "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":3},
        {"kind":"DOMAIN","topicValue":"DESIGN_PATTERN",    "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":4},
        {"kind":"DOMAIN","topicValue":"CACHING",           "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":5},
        {"kind":"DOMAIN","topicValue":"TESTING",           "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":6}
    ]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='BACKEND' AND level='mid' AND interview_type='CORE' AND is_default=TRUE
);

-- ─── FRONTEND-mid-CORE ───────────────────────────────────────────────────────
-- FE-shaped technical sweep: framework runtime + language depth + patterns +
-- testing, plus networking (HTTP/CDN) and security (XSS/CSRF basics).
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'FRONTEND', 'mid', 'CORE',
    '[
        {"kind":"DOMAIN","topicValue":"FRONTEND_DEV",      "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":1},
        {"kind":"DOMAIN","topicValue":"LANGUAGE_SPECIFIC", "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":2},
        {"kind":"DOMAIN","topicValue":"DESIGN_PATTERN",    "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":3},
        {"kind":"DOMAIN","topicValue":"TESTING",           "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":4},
        {"kind":"DOMAIN","topicValue":"NETWORKING",        "importance":"MED", "targetDifficulty":"EASY",  "orderHint":5},
        {"kind":"DOMAIN","topicValue":"SECURITY",          "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":6}
    ]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='FRONTEND' AND level='mid' AND interview_type='CORE' AND is_default=TRUE
);

-- ─── FULLSTACK-mid-CORE ──────────────────────────────────────────────────────
-- Pulls the canonical backend pillars (DB + system design) and folds in
-- frontend depth (FRONTEND_DEV) + a shared LANGUAGE_SPECIFIC + patterns.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'FULLSTACK', 'mid', 'CORE',
    '[
        {"kind":"DOMAIN","topicValue":"DATABASE",          "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":1},
        {"kind":"DOMAIN","topicValue":"SYSTEM_DESIGN",     "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":2},
        {"kind":"DOMAIN","topicValue":"FRONTEND_DEV",      "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":3},
        {"kind":"DOMAIN","topicValue":"LANGUAGE_SPECIFIC", "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":4},
        {"kind":"DOMAIN","topicValue":"DESIGN_PATTERN",    "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":5},
        {"kind":"DOMAIN","topicValue":"TESTING",           "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":6}
    ]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='FULLSTACK' AND level='mid' AND interview_type='CORE' AND is_default=TRUE
);
