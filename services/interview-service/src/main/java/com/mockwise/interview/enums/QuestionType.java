package com.mockwise.interview.enums;

/**
 * Mirror of question-bank's {@code QuestionType} enum, kept as a separate
 * type here so this service does not depend on the question-bank Java
 * classes. Names must stay byte-identical so JSON / event payloads
 * round-trip across services.
 */
public enum QuestionType {
    BEHAVIORAL,
    CORE_CONCEPTUAL,
    LIVE_CODING
}
