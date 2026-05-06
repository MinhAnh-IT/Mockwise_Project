package com.mockwise.interview.enums;

/**
 * The kind of topic tracked in session_topic_state.
 *
 * <ul>
 *   <li>{@link #COMPETENCY} — matches a {@code Competency} enum value in
 *       question-bank (e.g. {@code CONFLICT_RESOLUTION}). Used for
 *       BEHAVIORAL questions.</li>
 *   <li>{@link #DOMAIN} — matches a {@code Domain} enum value in
 *       question-bank (e.g. {@code DATABASE}). Used for CORE_CONCEPTUAL
 *       questions.</li>
 * </ul>
 *
 * <p>The actual value is stored as a {@code String topicValue} so this
 * service does not have to rebuild a coupling to question-bank's enums.
 */
public enum TopicKind {
    COMPETENCY,
    DOMAIN
}
