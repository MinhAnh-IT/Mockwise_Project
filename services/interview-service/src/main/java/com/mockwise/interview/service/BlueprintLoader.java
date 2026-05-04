package com.mockwise.interview.service;

import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.entity.SessionTopicStateId;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.TopicStatus;
import com.mockwise.interview.repository.InterviewBlueprintRepository;
import com.mockwise.interview.repository.SessionTopicStateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Owns the read side of the blueprint catalog: load the live default for a
 * given (role, level, type), and seed a session's topic-state matrix from
 * its topic list at /start.
 *
 * <p>The "first topic" pick (§3 of question-selection-design.md) lives
 * here too — it's a deterministic blueprint-only decision (no AI), so
 * keeping it next to the loader avoids leaking the rule into
 * SessionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BlueprintLoader {

    InterviewBlueprintRepository blueprintRepo;
    SessionTopicStateRepository topicStateRepo;

    /**
     * Resolves the live blueprint. Returns empty when none is configured —
     * the caller decides whether that's a 404 to the user or a server-side
     * config alert (e.g. dev environment without seeds).
     */
    public java.util.Optional<InterviewBlueprint> findFor(
            String targetRole, String level, InterviewType interviewType) {
        return blueprintRepo.findFirstByTargetRoleAndLevelAndInterviewTypeAndIsDefaultTrue(
                targetRole, level, interviewType);
    }

    /**
     * Inserts one {@link SessionTopicState} row per blueprint topic, all
     * starting at NOT_TESTED. Done in the same transaction as the session
     * insert so a partial failure rolls everything back.
     */
    public List<SessionTopicState> seedTopicStates(UUID sessionId, InterviewBlueprint blueprint) {
        List<SessionTopicState> rows = blueprint.getTopics().stream()
                .map(t -> SessionTopicState.builder()
                        .id(new SessionTopicStateId(sessionId, t.getKind(), t.getTopicValue()))
                        .status(TopicStatus.NOT_TESTED)
                        .importance(t.getImportance())
                        .targetDifficulty(t.getTargetDifficulty())
                        .questionsAsked(0)
                        .followUpsUsed(0)
                        .build())
                .toList();
        return topicStateRepo.saveAll(rows);
    }

    /**
     * Picks the opening topic. Sort key matches §3 of the design: lowest
     * order_hint first, breaking ties by importance DESC. The blueprint
     * is responsible for putting a low-stakes opener (e.g. self-intro)
     * at order_hint=1 — the loader does not second-guess that.
     *
     * <p>For MIXED / BEHAVIORAL types we additionally prefer COMPETENCY
     * topics so the very first question is open-ended; pure CORE
     * blueprints fall through to whatever topic the order_hint elects.
     */
    public BlueprintTopic pickFirstTopic(InterviewBlueprint blueprint) {
        if (blueprint.getTopics() == null || blueprint.getTopics().isEmpty()) {
            throw new IllegalStateException(
                    "Blueprint " + blueprint.getId() + " has no topics — cannot start a session");
        }
        Comparator<BlueprintTopic> base =
                Comparator.<BlueprintTopic>comparingInt(BlueprintTopic::getOrderHint);
        if (blueprint.getInterviewType() != InterviewType.CORE) {
            base = Comparator.<BlueprintTopic, Integer>comparing(
                    t -> t.getKind() == com.mockwise.interview.enums.TopicKind.COMPETENCY ? 0 : 1
            ).thenComparing(base);
        }
        return blueprint.getTopics().stream().min(base)
                .orElseThrow(); // unreachable after the empty check
    }
}
