package com.mockwise.interview.service;

import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.planner.PlannerSideEffects;
import com.mockwise.interview.repository.InterviewSessionRepository;
import com.mockwise.interview.repository.SessionTopicStateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Translates a {@link PlannerSideEffects} diff into JPA mutations against
 * the session + topic_state aggregates. Lives in the service layer (not
 * the planner) so the planner stays a pure function — see
 * {@code NextQuestionPlanner}'s class Javadoc.
 *
 * <p>Null fields in the diff mean "leave alone" by convention; the
 * absolute setters ({@code globalDifficultyOffsetSet},
 * {@code stretchModeSet}) overwrite when non-null. Deltas are added.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SideEffectApplier {

    InterviewSessionRepository sessionRepo;
    SessionTopicStateRepository topicStateRepo;

    /**
     * Applies the diff in place, then persists both rows. Caller must
     * hold a transaction so the topic / session updates land atomically.
     *
     * @param fx     mutation diff produced by the planner
     * @param session live session aggregate (mutated)
     * @param topic  live topic-state row of the topic the verdict relates to (mutated)
     */
    public void apply(PlannerSideEffects fx, InterviewSession session, SessionTopicState topic) {
        applyTopicMutations(fx, topic);
        applySessionMutations(fx, session);

        topicStateRepo.save(topic);
        sessionRepo.save(session);
    }

    private static void applyTopicMutations(PlannerSideEffects fx, SessionTopicState topic) {
        if (fx.newTopicStatus() != null) {
            topic.setStatus(fx.newTopicStatus());
        }
        if (fx.topicQuestionsAskedDelta() != null) {
            topic.setQuestionsAsked(topic.getQuestionsAsked() + fx.topicQuestionsAskedDelta());
        }
        if (fx.topicFollowUpsUsedDelta() != null) {
            topic.setFollowUpsUsed(topic.getFollowUpsUsed() + fx.topicFollowUpsUsedDelta());
        }
        if (fx.closeCurrentTopic() && topic.getClosedAt() == null) {
            topic.setClosedAt(OffsetDateTime.now());
        }
    }

    private static void applySessionMutations(PlannerSideEffects fx, InterviewSession session) {
        if (fx.sessionRunningStrongCountDelta() != null) {
            int next = session.getRunningStrongCount() + fx.sessionRunningStrongCountDelta();
            // Floor at zero — running_strong_count never logically goes negative.
            session.setRunningStrongCount(Math.max(0, next));
        }
        if (fx.sessionConsecutiveUnknownCountDelta() != null) {
            int next = session.getConsecutiveUnknownCount() + fx.sessionConsecutiveUnknownCountDelta();
            session.setConsecutiveUnknownCount(Math.max(0, next));
        }
        if (fx.sessionGlobalDifficultyOffsetSet() != null) {
            session.setGlobalDifficultyOffset(fx.sessionGlobalDifficultyOffsetSet());
        }
        if (fx.sessionStretchModeSet() != null) {
            session.setStretchMode(fx.sessionStretchModeSet());
        }
        if (fx.sessionTotalFollowUpsUsedDelta() != null) {
            int next = session.getTotalFollowUpsUsed() + fx.sessionTotalFollowUpsUsedDelta();
            session.setTotalFollowUpsUsed(Math.max(0, next));
        }
    }
}
