package com.mockwise.interview.planner;

import com.mockwise.interview.entity.enums.TopicStatus;

/**
 * The mutation diff a planner decision implies. Returned alongside the
 * {@link PlannerDecision} so the caller can apply both in the same DB
 * transaction without the planner ever touching JPA.
 *
 * <p>Each {@code Integer} field is a *delta* (signed). A null value means
 * "no change" — distinct from "set to zero". Same idea for the optional
 * absolute setters: a null means "leave it alone".
 *
 * <p>Trade-off: this couples the planner to the exact mutation surface
 * the orchestrator service has to apply. The benefit is that planner
 * tests can assert side effects without spinning up Spring + JPA, and
 * the orchestrator can reuse the same applier for a hypothetical replay
 * tool.
 */
public record PlannerSideEffects(
        // ── currentTopicState mutations ──────────────────────────────────────
        TopicStatus newTopicStatus,
        Integer topicQuestionsAskedDelta,
        Integer topicFollowUpsUsedDelta,
        boolean closeCurrentTopic,

        // ── session mutations ────────────────────────────────────────────────
        Integer sessionRunningStrongCountDelta,
        Integer sessionConsecutiveUnknownCountDelta,
        /** When non-null, OVERWRITES the existing offset rather than adding. */
        Integer sessionGlobalDifficultyOffsetSet,
        Boolean sessionStretchModeSet,
        Integer sessionTotalFollowUpsUsedDelta
) {

    /** Convenience builder for the common "no session-level change" case. */
    public static Builder forTopic(TopicStatus newStatus) {
        return new Builder().newTopicStatus(newStatus);
    }

    public static Builder noTopicChange() {
        return new Builder();
    }

    public static class Builder {
        private TopicStatus newTopicStatus;
        private Integer topicQuestionsAskedDelta;
        private Integer topicFollowUpsUsedDelta;
        private boolean closeCurrentTopic;
        private Integer sessionRunningStrongCountDelta;
        private Integer sessionConsecutiveUnknownCountDelta;
        private Integer sessionGlobalDifficultyOffsetSet;
        private Boolean sessionStretchModeSet;
        private Integer sessionTotalFollowUpsUsedDelta;

        public Builder newTopicStatus(TopicStatus s)        { this.newTopicStatus = s; return this; }
        public Builder questionsAskedDelta(int d)           { this.topicQuestionsAskedDelta = d; return this; }
        public Builder followUpsUsedDelta(int d)            { this.topicFollowUpsUsedDelta = d; return this; }
        public Builder closeTopic()                         { this.closeCurrentTopic = true; return this; }
        public Builder runningStrongCountDelta(int d)       { this.sessionRunningStrongCountDelta = d; return this; }
        public Builder consecutiveUnknownCountDelta(int d)  { this.sessionConsecutiveUnknownCountDelta = d; return this; }
        public Builder globalDifficultyOffsetSet(int v)     { this.sessionGlobalDifficultyOffsetSet = v; return this; }
        public Builder stretchModeSet(boolean v)            { this.sessionStretchModeSet = v; return this; }
        public Builder totalFollowUpsUsedDelta(int d)       { this.sessionTotalFollowUpsUsedDelta = d; return this; }

        public PlannerSideEffects build() {
            return new PlannerSideEffects(
                    newTopicStatus, topicQuestionsAskedDelta, topicFollowUpsUsedDelta,
                    closeCurrentTopic, sessionRunningStrongCountDelta,
                    sessionConsecutiveUnknownCountDelta, sessionGlobalDifficultyOffsetSet,
                    sessionStretchModeSet, sessionTotalFollowUpsUsedDelta);
        }
    }
}
