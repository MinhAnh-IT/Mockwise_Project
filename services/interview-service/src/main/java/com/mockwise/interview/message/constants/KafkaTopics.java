package com.mockwise.interview.message.constants;

/**
 * Topic name constants the orchestrator publishes to or consumes from.
 * Mirrors what the AI service ({@code AI/config.py}) and tts-stt-service
 * already expect, so changing one of these means a coordinated cross-
 * service change.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Produced after an answer's row hits PROCESSING. Consumed by tts-stt. */
    public static final String ANSWER_SUBMITTED      = "answer-submitted";

    /** Consumed: tts-stt finished and a transcript is on file. */
    public static final String TRANSCRIPT_READY      = "transcript-ready";

    /** Consumed: tts-stt failed. The orchestrator flips the answer to FAILED. */
    public static final String TRANSCRIPT_FAILED     = "transcript-failed";

    /** Produced when an answer is READY and we want the AI to score it. */
    public static final String EVALUATION_REQUESTED  = "evaluation-requested";

    /** Consumed: AI service published a verdict. */
    public static final String EVALUATION_COMPLETED  = "evaluation-completed";

    /** Consumed: AI service hit a hard error scoring an answer. */
    public static final String EVALUATION_FAILED     = "evaluation-failed";

    /** Produced for coding answers — judge-service consumes this. */
    public static final String CODE_SUBMISSION       = "code-submission";

    /** Consumed: judge-service published its verdict. */
    public static final String SUBMISSION_JUDGED     = "submission-judged";

    /** Produced when a session reaches SCORED — mail-service consumes for the report. */
    public static final String INTERVIEW_SCORED      = "interview-scored";

    /**
     * Produced when a session is COMPLETED and every answer has reached a
     * terminal state (SCORED or FAILED). Carries the full per-answer payload
     * so the AI can run an overall_reviewer graph in a single shot.
     */
    public static final String SESSION_EVALUATION_REQUESTED = "session-evaluation-requested";

    /** Consumed: AI published the cross-question review for a session. */
    public static final String SESSION_EVALUATION_COMPLETED = "session-evaluation-completed";

    /** Consumed: AI hit a hard error producing the overall review. */
    public static final String SESSION_EVALUATION_FAILED    = "session-evaluation-failed";
}
