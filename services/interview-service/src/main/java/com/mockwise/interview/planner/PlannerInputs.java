package com.mockwise.interview.planner;

import com.mockwise.interview.dto.assessment.AssessmentVerdict;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.enums.TopicStatus;

import java.util.Map;

/**
 * Immutable snapshot of everything the planner needs to make one decision.
 * Records ensure the planner cannot accidentally mutate caller state — the
 * caller is responsible for applying any side effects after seeing the
 * returned {@link PlannerOutcome}.
 *
 * <p>{@code currentTopicState} is the topic the {@code verdict} relates to
 * (i.e. the topic the just-answered question was probing).
 * {@code currentTopicConfig} is the matching blueprint entry, handed in
 * separately so the planner does not have to re-search the blueprint.
 *
 * <p>{@code allTopicStatuses} is a snapshot of every {@code SessionTopicState}
 * row's {@code status} keyed by {@code "kind:value"}. The planner uses this
 * to determine which topics are still NOT_TESTED when picking the next one,
 * so it never has to load entities itself.
 *
 * <p>{@code currentSessionQuestionCount} is total questions pinned to the
 * session so far (including follow-ups). Lets the planner enforce the
 * blueprint's question_budget without a JPA lookup.
 *
 * <p>{@code secondsRemaining} is wall-clock time left before the session
 * deadline ({@code startedAt + timeBudgetMinutes}); {@code secondsPerQuestion}
 * is the estimated cost of one more Q&A cycle (the blueprint's per-question
 * time share). Together they let the planner fill the time budget: when every
 * topic is already covered but a full cycle of time remains, it deepens
 * (re-probes the weakest covered topic) instead of ending early. Both are
 * computed by the orchestrator from the live session clock.
 */
public record PlannerInputs(
        InterviewSession session,
        InterviewBlueprint blueprint,
        SessionTopicState currentTopicState,
        BlueprintTopic currentTopicConfig,
        AssessmentVerdict verdict,
        Map<String, TopicStatus> allTopicStatuses,
        int currentSessionQuestionCount,
        int secondsRemaining,
        int secondsPerQuestion
) {
    /** "kind:value" — the canonical key the planner uses for topic identity. */
    public static String topicKey(BlueprintTopic t) {
        return t.getKind().name() + ":" + t.getTopicValue();
    }

    /**
     * True when enough interview time remains to fit at least one more full
     * question cycle. Guards the deepen branch so the planner never pins a
     * bonus question the candidate has no time to answer (it would be reaped
     * mid-answer by {@code SessionDeadlineReaper}).
     */
    public boolean hasTimeToDeepen() {
        return secondsPerQuestion > 0 && secondsRemaining >= secondsPerQuestion;
    }
}
