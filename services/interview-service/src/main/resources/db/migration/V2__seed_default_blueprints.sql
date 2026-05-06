-- Interview Service — seed default blueprints
--
-- Four (role, level, type) combinations are seeded as defaults so /start
-- works out of the box for the most common user paths. Admins can later
-- add more (e.g. FRONTEND-junior, BACKEND-senior, *-CORE-only) via an
-- admin endpoint or a future Vn migration.
--
-- topic_value strings must match question-bank's Competency / Domain
-- enum names byte-for-byte — see services/question-bank-service/src/main/
-- java/com/mockwise/questionbank/enums/{Competency,Domain}.java.
--
-- Topic JSON shape mirrors the BlueprintTopic Java record exactly
-- (kind, topicValue, importance, targetDifficulty, orderHint) so Jackson
-- deserialises into the entity's @Type(JsonBinaryType.class) List<BlueprintTopic>
-- without a custom converter.

-- ─── BACKEND-junior-MIXED ────────────────────────────────────────────────────
-- Lighter mix: warm-up behavioral, two foundational technical topics, one
-- code-quality concept. Smaller budget to reflect a shorter interview.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'junior', 'MIXED',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",         "importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"TEAMWORK",          "importance":"MED", "targetDifficulty":"EASY",   "orderHint":2},
        {"kind":"DOMAIN",    "topicValue":"DATABASE",          "importance":"HIGH","targetDifficulty":"EASY",   "orderHint":3},
        {"kind":"DOMAIN",    "topicValue":"DESIGN_PATTERN",    "importance":"HIGH","targetDifficulty":"EASY",   "orderHint":4},
        {"kind":"DOMAIN",    "topicValue":"TESTING",           "importance":"MED", "targetDifficulty":"EASY",   "orderHint":5}
    ]'::jsonb,
    6, 1, 2, 30, FALSE, TRUE
);

-- ─── BACKEND-mid-MIXED (the default for /start when no override) ─────────────
-- Six topics covering the canonical backend interview shape. Two HIGH-
-- importance behaviorals up front (warm-up + a real conflict story) then
-- the technical core: DB → System design → Patterns → Caching.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'mid', 'MIXED',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",            "importance":"MED", "targetDifficulty":"EASY",  "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"CONFLICT_RESOLUTION",  "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":2},
        {"kind":"DOMAIN",    "topicValue":"DATABASE",             "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":3},
        {"kind":"DOMAIN",    "topicValue":"SYSTEM_DESIGN",        "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":4},
        {"kind":"DOMAIN",    "topicValue":"DESIGN_PATTERN",       "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":5},
        {"kind":"DOMAIN",    "topicValue":"CACHING",              "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":6}
    ]'::jsonb,
    8, 2, 4, 45, FALSE, TRUE
);

-- ─── FRONTEND-mid-MIXED ──────────────────────────────────────────────────────
-- Frontend pivots from system_design / caching toward language-specific
-- depth (TypeScript / framework runtime) + design_pattern + testing.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'mid', 'MIXED',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",          "importance":"MED", "targetDifficulty":"EASY",  "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"COMMUNICATION",      "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":2},
        {"kind":"DOMAIN",    "topicValue":"FRONTEND_DEV",       "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":3},
        {"kind":"DOMAIN",    "topicValue":"LANGUAGE_SPECIFIC",  "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":4},
        {"kind":"DOMAIN",    "topicValue":"DESIGN_PATTERN",     "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":5},
        {"kind":"DOMAIN",    "topicValue":"TESTING",            "importance":"MED", "targetDifficulty":"EASY",  "orderHint":6}
    ]'::jsonb,
    8, 2, 4, 45, FALSE, TRUE
);

-- ─── FULLSTACK-mid-MIXED ─────────────────────────────────────────────────────
-- Mid-fullstack covers both sides — DB + system design from the backend
-- pile + frontend_dev from the FE pile, plus pattern-thinking glue.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'mid', 'MIXED',
    '[
        {"kind":"COMPETENCY","topicValue":"OWNERSHIP",            "importance":"MED", "targetDifficulty":"EASY",  "orderHint":1},
        {"kind":"COMPETENCY","topicValue":"CONFLICT_RESOLUTION",  "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":2},
        {"kind":"DOMAIN",    "topicValue":"DATABASE",             "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":3},
        {"kind":"DOMAIN",    "topicValue":"SYSTEM_DESIGN",        "importance":"HIGH","targetDifficulty":"MEDIUM","orderHint":4},
        {"kind":"DOMAIN",    "topicValue":"FRONTEND_DEV",         "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":5},
        {"kind":"DOMAIN",    "topicValue":"DESIGN_PATTERN",       "importance":"MED", "targetDifficulty":"MEDIUM","orderHint":6}
    ]'::jsonb,
    8, 2, 4, 45, FALSE, TRUE
);
