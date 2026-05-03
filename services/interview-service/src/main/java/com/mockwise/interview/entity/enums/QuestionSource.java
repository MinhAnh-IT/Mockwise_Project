package com.mockwise.interview.entity.enums;

/**
 * Where a session_question's content came from.
 *
 * <ul>
 *   <li>{@link #BANK} — pinned from question-bank; {@code question_id} is
 *       set, {@code inline_text} is null.</li>
 *   <li>{@link #PRE_AUTHORED_FOLLOWUP} — pulled from question-bank's
 *       {@code question_follow_up} table; {@code parent_question_id}
 *       points at the parent in the bank.</li>
 *   <li>{@link #AI_GENERATED} — generated on-the-fly by ai-service's
 *       {@code POST /follow-up/generate}; {@code question_id} is null,
 *       content lives in {@code inline_text} + {@code inline_expected_points}.
 *       Not written back to question-bank — these are session-scoped only.</li>
 * </ul>
 */
public enum QuestionSource {
    BANK,
    PRE_AUTHORED_FOLLOWUP,
    AI_GENERATED
}
