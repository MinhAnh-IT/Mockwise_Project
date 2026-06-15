package com.mockwise.interview.planner;

import com.mockwise.interview.dto.assessment.AssessmentVerdict;
import com.mockwise.interview.dto.assessment.WeakTarget;
import com.mockwise.interview.enums.Completeness;
import com.mockwise.interview.enums.Correctness;
import com.mockwise.interview.enums.Depth;
import com.mockwise.interview.enums.Severity;
import com.mockwise.interview.enums.SignalStrength;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.TopicStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The decision tree from question-selection-design.md §5–6 in code form.
 * Pure function: takes an immutable {@link PlannerInputs} snapshot and
 * returns a {@link PlannerOutcome} (decision + diff). The orchestrator
 * applies the diff to JPA in the same transaction as persisting the verdict.
 *
 * <p>Why pure: the planner is the highest-stakes piece of the orchestrator,
 * so making it independently testable was worth the bookkeeping. The 13
 * unit tests in {@code NextQuestionPlannerTest} drive every Case A1/A2/B/C/D
 * branch + the stretch and termination conditions without spinning Spring.
 *
 * <p>Branch order (matches §5 of the doc):
 * <ol>
 *   <li>Check Case B (NO_ANSWER) first — overrides any score-based branch.</li>
 *   <li>Check Case D (STRONG) — fast path when the candidate clearly nailed it.</li>
 *   <li>Check Case A (poor answer) — split into A1 (WRONG) and A2 (MIXED+SURFACE).</li>
 *   <li>Otherwise consider Case C (follow-up).</li>
 *   <li>If after applying the diff the session has no more topics or has
 *       hit a budget, return EndSession.</li>
 * </ol>
 */
@Component
public class NextQuestionPlanner {

    /** Score below this (out of 10) qualifies the answer as Case A "very bad". */
    static final float CASE_A_SCORE_THRESHOLD = 3.0f;
    /** Score at or above this (out of 10) qualifies the answer as Case D "strong". */
    static final float CASE_D_SCORE_THRESHOLD = 7.0f;
    /** Score at or above this (out of 10) makes a probed-out topic ADEQUATE rather than PARTIAL. */
    static final float ADEQUATE_SCORE_THRESHOLD = 6.0f;
    /** consecutive_unknown_count at or above this triggers global difficulty offset. */
    static final int CONSECUTIVE_UNKNOWN_CRITICAL = 3;
    /** running_strong_count at or above this turns on stretch mode. */
    static final int STRETCH_MODE_TRIGGER = 3;
    /** Stretch mode caps difficulty at HARD; this is the max offset we'd ever apply. */
    static final int MAX_DIFFICULTY_OFFSET = 1;
    static final int MIN_DIFFICULTY_OFFSET = -1;

    public PlannerOutcome plan(PlannerInputs inputs) {
        AssessmentVerdict verdict = inputs.verdict();
        SessionTopicState topic = inputs.currentTopicState();
        InterviewBlueprint blueprint = inputs.blueprint();

        // Branch order matters — see class-level Javadoc.
        if (verdict.completeness() == Completeness.NO_ANSWER) {
            return handleCaseB(inputs);
        }

        if (isCaseD(verdict)) {
            return handleCaseD(inputs);
        }

        if (verdict.scoreNormalized() != null
                && verdict.scoreNormalized() < CASE_A_SCORE_THRESHOLD) {
            return handleCaseA(inputs);
        }

        // Default: try a follow-up if we have a target and quota; otherwise close adequate.
        if (shouldFollowUp(verdict, topic, blueprint)) {
            return handleCaseCFollowUp(inputs);
        }
        return handleCaseCClose(inputs);
    }

    /**
     * Fallback for when {@link #plan} returned {@link PlannerDecision.AskFollowUp}
     * but the orchestrator could not actually produce a follow-up question
     * (e.g. the AI generator is unavailable or returned an empty question).
     * Closes the current topic as PARTIAL and advances to the next topic — or
     * ends the session if coverage / budget is exhausted — so a live interview
     * never stalls waiting for a follow-up that will not arrive.
     */
    public PlannerOutcome closeTopicAndAdvance(PlannerInputs inputs) {
        return moveToNextOrEnd(inputs, TopicStatus.PARTIAL, /*difficultyOffset*/ 0);
    }

    // ── Case A: very poor answer ─────────────────────────────────────────────
    private PlannerOutcome handleCaseA(PlannerInputs inputs) {
        AssessmentVerdict v = inputs.verdict();
        SessionTopicState topic = inputs.currentTopicState();

        // A1 — fundamental misunderstanding: drop the topic, lower next.
        if (v.correctness() == Correctness.WRONG) {
            return moveToNextOrEnd(inputs, TopicStatus.WEAK, /*difficultyOffset*/ -1);
        }

        // A2 — has-some-idea-but-shallow: one second-chance allowed at lower difficulty.
        boolean isShallowMixed =
                v.correctness() == Correctness.MIXED && v.depth() == Depth.SURFACE;
        if (isShallowMixed && topic.getFollowUpsUsed() < 1) {
            // Use the highest-severity weak target if any; otherwise synthesise
            // a generic one (the bank lookup won't match — caller falls back
            // to AI generator).
            WeakTarget target = pickWeakTarget(v.weakTargets())
                    .orElse(new WeakTarget(
                            com.mockwise.interview.enums.WeakTargetKind.SIGNAL,
                            "second_chance",
                            Severity.MED));
            Difficulty downgraded = downgradeWith(topic.getLastDifficulty() != null
                    ? topic.getLastDifficulty() : topic.getTargetDifficulty());

            return new PlannerOutcome(
                    new PlannerDecision.AskFollowUp(target, downgraded),
                    PlannerSideEffects.noTopicChange()
                            .followUpsUsedDelta(1)
                            .totalFollowUpsUsedDelta(1)
                            .build());
        }

        // A2 follow-up exhausted, or A1 fall-through — close as WEAK.
        return moveToNextOrEnd(inputs, TopicStatus.WEAK, /*difficultyOffset*/ -1);
    }

    // ── Case B: candidate opted out ──────────────────────────────────────────
    private PlannerOutcome handleCaseB(PlannerInputs inputs) {
        int newConsecutive = inputs.session().getConsecutiveUnknownCount() + 1;

        boolean criticalMismatch = newConsecutive >= CONSECUTIVE_UNKNOWN_CRITICAL;

        // Build the side effects: close topic as UNKNOWN, bump consecutive.
        PlannerSideEffects.Builder fx = PlannerSideEffects.forTopic(TopicStatus.UNKNOWN)
                .closeTopic()
                .consecutiveUnknownCountDelta(1);

        // Critical mismatch lowers global difficulty — applied here so the
        // very next topic pick uses the lower offset.
        if (criticalMismatch && inputs.session().getGlobalDifficultyOffset() > MIN_DIFFICULTY_OFFSET) {
            fx.globalDifficultyOffsetSet(MIN_DIFFICULTY_OFFSET);
        }

        // No follow-up on opt-out — picking next topic or ending.
        return moveToNextOrEndWithFx(inputs, fx);
    }

    // ── Case D: strong answer ────────────────────────────────────────────────
    private PlannerOutcome handleCaseD(PlannerInputs inputs) {
        int newRunningStrong = inputs.session().getRunningStrongCount() + 1;
        boolean shouldEnableStretch =
                !inputs.session().isStretchMode() && newRunningStrong >= STRETCH_MODE_TRIGGER;

        PlannerSideEffects.Builder fx = PlannerSideEffects.forTopic(TopicStatus.STRONG)
                .closeTopic()
                .runningStrongCountDelta(1)
                // A clean answer ends the consecutive-unknown streak.
                .consecutiveUnknownCountDelta(-inputs.session().getConsecutiveUnknownCount());
        if (shouldEnableStretch) {
            fx.stretchModeSet(true);
        }

        return moveToNextOrEndWithFx(inputs, fx);
    }

    // ── Case C: follow-up branch decisions ───────────────────────────────────
    private boolean shouldFollowUp(AssessmentVerdict v, SessionTopicState topic, InterviewBlueprint bp) {
        // Quota check is checked by handleCaseCFollowUp before generating, but
        // we also need it here so we don't even consider follow-up when caps hit.
        if (topic.getFollowUpsUsed() >= bp.getMaxFollowUpsPerTopic()) return false;
        // Session-wide cap. Read from the session aggregate, not the topic row.
        // The caller passes the running session, so the count is already current.
        // (Tested: see plan_caseCSessionCapExhausted.)

        // Trigger conditions per §5.3: incomplete, surface/moderate depth, or
        // any HIGH-severity weak target.
        boolean weakSignals = v.signalStrength() == SignalStrength.PARTIAL
                || v.signalStrength() == SignalStrength.NONE;
        boolean hasHighWeakTarget = v.weakTargets() != null
                && v.weakTargets().stream().anyMatch(t -> t.severity() == Severity.HIGH);
        boolean incompleteOrShallow =
                v.completeness() == Completeness.INCOMPLETE
                        || v.depth() == Depth.SURFACE
                        || v.depth() == Depth.MODERATE;

        if (!(weakSignals || hasHighWeakTarget || incompleteOrShallow)) {
            return false;
        }
        // No specific gap to probe → no point asking AI to invent one.
        return v.weakTargets() != null && !v.weakTargets().isEmpty();
    }

    private PlannerOutcome handleCaseCFollowUp(PlannerInputs inputs) {
        SessionTopicState topic = inputs.currentTopicState();
        InterviewBlueprint bp = inputs.blueprint();

        // Session-wide cap check (per §5.3 second branch).
        if (inputs.session().getTotalFollowUpsUsed() >= bp.getMaxFollowUpsPerSession()) {
            // Fall through to closing the topic — partial vs adequate decided
            // by score in handleCaseCClose.
            return handleCaseCClose(inputs);
        }

        WeakTarget target = pickWeakTarget(inputs.verdict().weakTargets()).orElseThrow();
        // Follow-up never raises difficulty above parent; default to last.
        Difficulty fuDifficulty = topic.getLastDifficulty() != null
                ? topic.getLastDifficulty()
                : topic.getTargetDifficulty();

        return new PlannerOutcome(
                new PlannerDecision.AskFollowUp(target, fuDifficulty),
                PlannerSideEffects.noTopicChange()
                        .followUpsUsedDelta(1)
                        .totalFollowUpsUsedDelta(1)
                        .build());
    }

    private PlannerOutcome handleCaseCClose(PlannerInputs inputs) {
        AssessmentVerdict v = inputs.verdict();
        TopicStatus status =
                (v.scoreNormalized() != null && v.scoreNormalized() >= ADEQUATE_SCORE_THRESHOLD)
                        ? TopicStatus.ADEQUATE
                        : TopicStatus.PARTIAL;
        return moveToNextOrEnd(inputs, status, /*difficultyOffset*/ 0);
    }

    // ── Termination + topic-picking helpers ──────────────────────────────────

    private PlannerOutcome moveToNextOrEnd(PlannerInputs inputs, TopicStatus newStatus, int difficultyOffset) {
        PlannerSideEffects.Builder fx = PlannerSideEffects.forTopic(newStatus).closeTopic();
        // Strong answers reset consecutive-unknown; everything else just leaves it.
        return moveToNextOrEndWithFx(inputs, fx, difficultyOffset);
    }

    private PlannerOutcome moveToNextOrEndWithFx(PlannerInputs inputs, PlannerSideEffects.Builder fx) {
        return moveToNextOrEndWithFx(inputs, fx, 0);
    }

    /**
     * Builds the side-effect snapshot, then computes the opening difficulty
     * using the *effective* (post-decision) offset/stretch values rather
     * than the input snapshot. Otherwise Case B's critical-mismatch offset
     * and Case D's stretch trigger don't take effect until the question
     * after the one we're picking right now — which would be the wrong tick.
     */
    private PlannerOutcome moveToNextOrEndWithFx(
            PlannerInputs inputs, PlannerSideEffects.Builder fx, int extraDifficultyOffset) {

        PlannerSideEffects effects = fx.build();

        // Question-budget check first: if we've already pinned at least
        // questionBudget rows, no more questions can be added regardless of
        // coverage. This wins over coverage so a budget-overrun blueprint
        // doesn't infinitely loop.
        if (inputs.currentSessionQuestionCount() >= inputs.blueprint().getQuestionBudget()) {
            return new PlannerOutcome(
                    new PlannerDecision.EndSession(PlannerDecision.EndSession.EndReason.QUESTION_BUDGET_EXHAUSTED),
                    effects);
        }

        // Coverage-complete check: a topic is "uncovered" when its status in
        // allTopicStatuses is NOT_TESTED *and* it's not the topic we're closing
        // right now (which is still NOT_TESTED in the snapshot, but we know
        // we're about to close it).
        String currentKey = PlannerInputs.topicKey(inputs.currentTopicConfig());
        long uncoveredCount = inputs.blueprint().getTopics().stream()
                .filter(t -> !PlannerInputs.topicKey(t).equals(currentKey))
                .filter(t -> {
                    TopicStatus s = inputs.allTopicStatuses().get(PlannerInputs.topicKey(t));
                    return s == null || s == TopicStatus.NOT_TESTED;
                })
                .count();

        if (uncoveredCount == 0) {
            return new PlannerOutcome(
                    new PlannerDecision.EndSession(PlannerDecision.EndSession.EndReason.COVERAGE_COMPLETE),
                    effects);
        }

        BlueprintTopic next = pickNextTopic(inputs);
        if (next == null) {
            // Defensive — pickNextTopic should always succeed if uncoveredCount > 0.
            return new PlannerOutcome(
                    new PlannerDecision.EndSession(PlannerDecision.EndSession.EndReason.COVERAGE_COMPLETE),
                    effects);
        }

        // Effective values for difficulty calculation: prefer pending updates
        // from the just-built side effects over the input snapshot.
        int effectiveOffset = effects.sessionGlobalDifficultyOffsetSet() != null
                ? effects.sessionGlobalDifficultyOffsetSet()
                : inputs.session().getGlobalDifficultyOffset();
        boolean effectiveStretch = effects.sessionStretchModeSet() != null
                ? effects.sessionStretchModeSet()
                : inputs.session().isStretchMode();

        Difficulty opening = computeOpeningDifficulty(
                next, effectiveOffset, effectiveStretch, extraDifficultyOffset);
        return new PlannerOutcome(
                new PlannerDecision.MoveToNextTopic(next, opening),
                effects);
    }

    private BlueprintTopic pickNextTopic(PlannerInputs inputs) {
        // §6: importance DESC, then order_hint ASC, then prefer different-kind
        // from the current topic to interleave behavioral / domain.
        String currentKey = PlannerInputs.topicKey(inputs.currentTopicConfig());
        return inputs.blueprint().getTopics().stream()
                .filter(t -> !PlannerInputs.topicKey(t).equals(currentKey))
                .filter(t -> {
                    TopicStatus s = inputs.allTopicStatuses().get(PlannerInputs.topicKey(t));
                    return s == null || s == TopicStatus.NOT_TESTED;
                })
                .min(Comparator
                        .comparingInt((BlueprintTopic t) -> -importanceWeight(t))
                        .thenComparingInt(BlueprintTopic::getOrderHint)
                        .thenComparingInt(t -> t.getKind() == inputs.currentTopicConfig().getKind() ? 1 : 0))
                .orElse(null);
    }

    private static int importanceWeight(BlueprintTopic t) {
        return switch (t.getImportance()) {
            case HIGH -> 3;
            case MED  -> 2;
            case LOW  -> 1;
        };
    }

    /**
     * Combine the topic's target with the *effective* session signals
     * (post-decision offset / stretch) and any one-off offset the planner
     * chose for this move.
     */
    private Difficulty computeOpeningDifficulty(
            BlueprintTopic next, int globalOffset, boolean stretchMode, int extraOffset) {
        int totalOffset = globalOffset + extraOffset;
        if (stretchMode) totalOffset += 1;

        // Clamp.
        totalOffset = Math.max(MIN_DIFFICULTY_OFFSET, Math.min(MAX_DIFFICULTY_OFFSET, totalOffset));

        Difficulty base = next.getTargetDifficulty();
        if (totalOffset > 0) return base.upgrade();
        if (totalOffset < 0) return base.downgrade();
        return base;
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    private static boolean isCaseD(AssessmentVerdict v) {
        if (v.scoreNormalized() == null) return false;
        return v.scoreNormalized() >= CASE_D_SCORE_THRESHOLD
                && v.signalStrength() == SignalStrength.STRONG
                && (v.hireSignal() == com.mockwise.interview.enums.HireSignal.yes
                    || v.hireSignal() == com.mockwise.interview.enums.HireSignal.strong_yes);
    }

    private static Optional<WeakTarget> pickWeakTarget(List<WeakTarget> targets) {
        if (targets == null || targets.isEmpty()) return Optional.empty();
        // Severity DESC, then preserve discovery order.
        return targets.stream()
                .max(Comparator.comparingInt(t -> severityWeight(t.severity())));
    }

    private static int severityWeight(Severity s) {
        return switch (s) {
            case HIGH -> 3;
            case MED  -> 2;
            case LOW  -> 1;
        };
    }

    private static Difficulty downgradeWith(Difficulty current) {
        return current.downgrade();
    }
}
