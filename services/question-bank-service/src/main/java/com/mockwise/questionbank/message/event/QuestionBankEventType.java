package com.mockwise.questionbank.message.event;

public enum QuestionBankEventType {
    /** Question transitioned into ACTIVE status — consumers should index/embed it. */
    QUESTION_ACTIVATED,
    /** Embedding-relevant fields changed while the question is ACTIVE — consumers should re-embed. */
    QUESTION_UPDATED,
    /** Question moved out of ACTIVE (INACTIVE / soft delete) — consumers should remove from index. */
    QUESTION_DEACTIVATED
}
