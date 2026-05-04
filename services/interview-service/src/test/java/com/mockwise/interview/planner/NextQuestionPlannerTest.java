package com.mockwise.interview.planner;

import com.mockwise.interview.assessment.AssessmentVerdict;
import com.mockwise.interview.assessment.StrongTarget;
import com.mockwise.interview.assessment.WeakTarget;
import com.mockwise.interview.assessment.enums.Completeness;
import com.mockwise.interview.assessment.enums.Correctness;
import com.mockwise.interview.assessment.enums.Depth;
import com.mockwise.interview.assessment.enums.Grade;
import com.mockwise.interview.assessment.enums.HireSignal;
import com.mockwise.interview.assessment.enums.Severity;
import com.mockwise.interview.assessment.enums.SignalStrength;
import com.mockwise.interview.assessment.enums.WeakTargetKind;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.entity.SessionTopicStateId;
import com.mockwise.interview.entity.enums.Difficulty;
import com.mockwise.interview.entity.enums.Importance;
import com.mockwise.interview.entity.enums.SessionStatus;
import com.mockwise.interview.entity.enums.TopicKind;
import com.mockwise.interview.entity.enums.TopicStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One test per Case in question-selection-design.md §5–6 plus the cross-
 * cutting termination rules. The planner is a pure function over
 * {@link PlannerInputs}, so every test builds a fresh fixture, calls
 * {@code plan(inputs)}, and asserts on the returned {@link PlannerOutcome}.
 */
class NextQuestionPlannerTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private final NextQuestionPlanner planner = new NextQuestionPlanner();

    // ── Case A1: WRONG correctness — drop topic, lower next ──────────────────

    @Test
    void caseA1_wrongCorrectness_marksWeak_movesNextWithLowerDifficulty() {
        var fx = Fixtures.aDefaultBlueprint(); // 3 topics
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(2.0f, SignalStrength.NONE, Correctness.WRONG, Depth.SURFACE,
                        Completeness.COMPLETE, HireSignal.strong_no, Grade.F, List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().newTopicStatus()).isEqualTo(TopicStatus.WEAK);
        assertThat(out.sideEffects().closeCurrentTopic()).isTrue();
        assertThat(out.decision()).isInstanceOf(PlannerDecision.MoveToNextTopic.class);
        var move = (PlannerDecision.MoveToNextTopic) out.decision();
        // Next topic from blueprint at MEDIUM, downgrade -1 → EASY
        assertThat(move.openingDifficulty()).isEqualTo(Difficulty.EASY);
    }

    // ── Case A2: MIXED + SURFACE, first attempt — second-chance follow-up ────

    @Test
    void caseA2_mixedShallow_firstAttempt_offersSecondChanceFollowUp() {
        var fx = Fixtures.aDefaultBlueprint();
        var weak = new WeakTarget(WeakTargetKind.SIGNAL, "specific_recent_example", Severity.HIGH);
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(2.5f, SignalStrength.PARTIAL, Correctness.MIXED, Depth.SURFACE,
                        Completeness.INCOMPLETE, HireSignal.no, Grade.F,
                        List.of(weak), List.of()))
                .topicState(Fixtures.topicStateBuilder(fx)
                        .followUpsUsed(0)
                        .lastDifficulty(Difficulty.MEDIUM)
                        .build())
                .build();

        var out = planner.plan(inputs);

        assertThat(out.decision()).isInstanceOf(PlannerDecision.AskFollowUp.class);
        var fu = (PlannerDecision.AskFollowUp) out.decision();
        assertThat(fu.weakTarget()).isEqualTo(weak);
        // Second chance: easier than parent
        assertThat(fu.difficulty()).isEqualTo(Difficulty.EASY);

        assertThat(out.sideEffects().topicFollowUpsUsedDelta()).isEqualTo(1);
        assertThat(out.sideEffects().sessionTotalFollowUpsUsedDelta()).isEqualTo(1);
        assertThat(out.sideEffects().closeCurrentTopic()).isFalse();
    }

    // ── Case A2 second attempt: still bad — close as WEAK ────────────────────

    @Test
    void caseA2_mixedShallow_secondAttemptStillFails_closesAsWeak() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(2.5f, SignalStrength.PARTIAL, Correctness.MIXED, Depth.SURFACE,
                        Completeness.INCOMPLETE, HireSignal.no, Grade.F,
                        List.of(new WeakTarget(WeakTargetKind.SIGNAL, "x", Severity.HIGH)),
                        List.of()))
                .topicState(Fixtures.topicStateBuilder(fx)
                        .followUpsUsed(1) // already used the one A2 second-chance
                        .lastDifficulty(Difficulty.EASY)
                        .build())
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().newTopicStatus()).isEqualTo(TopicStatus.WEAK);
        assertThat(out.sideEffects().closeCurrentTopic()).isTrue();
        assertThat(out.decision()).isInstanceOf(PlannerDecision.MoveToNextTopic.class);
    }

    // ── Case B: NO_ANSWER — UNKNOWN, increment consecutive, no follow-up ─────

    @Test
    void caseB_noAnswer_marksUnknown_incrementsConsecutive_movesNextSameDifficulty() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(0f, SignalStrength.NONE, Correctness.MIXED, Depth.SURFACE,
                        Completeness.NO_ANSWER, HireSignal.strong_no, Grade.F, List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().newTopicStatus()).isEqualTo(TopicStatus.UNKNOWN);
        assertThat(out.sideEffects().sessionConsecutiveUnknownCountDelta()).isEqualTo(1);
        assertThat(out.sideEffects().sessionGlobalDifficultyOffsetSet()).isNull();
        assertThat(out.decision()).isInstanceOf(PlannerDecision.MoveToNextTopic.class);
    }

    // ── Case B critical mismatch: 3rd consecutive UNKNOWN flips global offset ─

    @Test
    void caseB_criticalMismatch_lowersGlobalDifficultyOffset() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .session(Fixtures.sessionBuilder()
                        .consecutiveUnknownCount(2) // about to become 3
                        .build())
                .verdict(verdict(0f, SignalStrength.NONE, Correctness.MIXED, Depth.SURFACE,
                        Completeness.NO_ANSWER, HireSignal.strong_no, Grade.F, List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().sessionGlobalDifficultyOffsetSet()).isEqualTo(-1);
        // Picked next topic should reflect the new offset → next topic at MEDIUM downgraded to EASY
        var move = (PlannerDecision.MoveToNextTopic) out.decision();
        assertThat(move.openingDifficulty()).isEqualTo(Difficulty.EASY);
    }

    // ── Case C: incomplete + weak target + under quota → AskFollowUp ─────────

    @Test
    void caseC_incompleteWithHighWeakTarget_underQuota_asksFollowUp() {
        var fx = Fixtures.aDefaultBlueprint();
        var weak = new WeakTarget(WeakTargetKind.CONCEPT, "composite_index_order", Severity.HIGH);
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(5.5f, SignalStrength.PARTIAL, Correctness.MIXED, Depth.MODERATE,
                        Completeness.INCOMPLETE, HireSignal.weak_yes, Grade.D,
                        List.of(weak), List.of()))
                .topicState(Fixtures.topicStateBuilder(fx)
                        .followUpsUsed(0)
                        .lastDifficulty(Difficulty.MEDIUM)
                        .build())
                .build();

        var out = planner.plan(inputs);

        assertThat(out.decision()).isInstanceOf(PlannerDecision.AskFollowUp.class);
        var fu = (PlannerDecision.AskFollowUp) out.decision();
        assertThat(fu.weakTarget()).isEqualTo(weak);
        // Follow-up at same difficulty as parent (not raised, not lowered)
        assertThat(fu.difficulty()).isEqualTo(Difficulty.MEDIUM);

        assertThat(out.sideEffects().topicFollowUpsUsedDelta()).isEqualTo(1);
        assertThat(out.sideEffects().sessionTotalFollowUpsUsedDelta()).isEqualTo(1);
        assertThat(out.sideEffects().closeCurrentTopic()).isFalse();
        assertThat(out.sideEffects().newTopicStatus()).isNull();
    }

    // ── Case C: per-topic quota exhausted → close, status by score ───────────

    @Test
    void caseC_perTopicQuotaExhausted_closesAsAdequate_whenScoreAtThreshold() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(6.5f, SignalStrength.ADEQUATE, Correctness.MIXED, Depth.MODERATE,
                        Completeness.INCOMPLETE, HireSignal.weak_yes, Grade.C,
                        List.of(new WeakTarget(WeakTargetKind.CONCEPT, "x", Severity.HIGH)),
                        List.of()))
                .topicState(Fixtures.topicStateBuilder(fx)
                        .followUpsUsed(2) // == max_follow_ups_per_topic in Fixtures
                        .lastDifficulty(Difficulty.MEDIUM)
                        .build())
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().newTopicStatus()).isEqualTo(TopicStatus.ADEQUATE);
        assertThat(out.sideEffects().closeCurrentTopic()).isTrue();
        assertThat(out.decision()).isInstanceOf(PlannerDecision.MoveToNextTopic.class);
    }

    // ── Case C: session-wide cap hit → close as PARTIAL when score below threshold ─

    @Test
    void caseC_sessionCapExhausted_closesAsPartial_whenScoreBelowThreshold() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .session(Fixtures.sessionBuilder()
                        .totalFollowUpsUsed(4) // == max_follow_ups_per_session
                        .build())
                .verdict(verdict(5.0f, SignalStrength.PARTIAL, Correctness.MIXED, Depth.MODERATE,
                        Completeness.INCOMPLETE, HireSignal.weak_yes, Grade.D,
                        List.of(new WeakTarget(WeakTargetKind.CONCEPT, "x", Severity.HIGH)),
                        List.of()))
                .topicState(Fixtures.topicStateBuilder(fx)
                        .followUpsUsed(0)
                        .lastDifficulty(Difficulty.MEDIUM)
                        .build())
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().newTopicStatus()).isEqualTo(TopicStatus.PARTIAL);
        assertThat(out.sideEffects().closeCurrentTopic()).isTrue();
        assertThat(out.decision()).isInstanceOf(PlannerDecision.MoveToNextTopic.class);
    }

    // ── Case C: empty weak targets — nothing to probe → close as ADEQUATE ────

    @Test
    void caseC_emptyWeakTargets_closesAsAdequate() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(6.0f, SignalStrength.ADEQUATE, Correctness.MIXED, Depth.MODERATE,
                        Completeness.INCOMPLETE, HireSignal.weak_yes, Grade.C,
                        List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().newTopicStatus()).isEqualTo(TopicStatus.ADEQUATE);
        assertThat(out.decision()).isInstanceOf(PlannerDecision.MoveToNextTopic.class);
    }

    // ── Case D: STRONG — close, increment running_strong, move next ──────────

    @Test
    void caseD_strongAnswer_closesStrong_incrementsRunning_movesNext() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(8.5f, SignalStrength.STRONG, Correctness.CORRECT, Depth.DEEP,
                        Completeness.COMPLETE, HireSignal.yes, Grade.B,
                        List.of(),
                        List.of(new StrongTarget(
                                com.mockwise.interview.assessment.enums.StrongTargetKind.SIGNAL, "x"))))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().newTopicStatus()).isEqualTo(TopicStatus.STRONG);
        assertThat(out.sideEffects().sessionRunningStrongCountDelta()).isEqualTo(1);
        assertThat(out.decision()).isInstanceOf(PlannerDecision.MoveToNextTopic.class);
    }

    // ── Stretch trigger: 3rd consecutive STRONG flips stretch_mode ───────────

    @Test
    void stretchMode_triggers_onThirdConsecutiveStrong_andUpgradesNextDifficulty() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .session(Fixtures.sessionBuilder()
                        .runningStrongCount(2) // about to become 3
                        .build())
                .verdict(verdict(9.0f, SignalStrength.STRONG, Correctness.CORRECT, Depth.DEEP,
                        Completeness.COMPLETE, HireSignal.strong_yes, Grade.A,
                        List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.sideEffects().sessionStretchModeSet()).isTrue();
        // Next topic at MEDIUM (Domain blueprint topic) but stretch +1 → HARD
        var move = (PlannerDecision.MoveToNextTopic) out.decision();
        assertThat(move.openingDifficulty()).isEqualTo(Difficulty.HARD);
    }

    // ── End session: coverage complete ───────────────────────────────────────

    @Test
    void endsSession_whenCoverageComplete() {
        var fx = Fixtures.aDefaultBlueprint();
        // Mark all OTHER topics as already STRONG so the only un-tested topic
        // is the one we're closing right now.
        Map<String, TopicStatus> statuses = new LinkedHashMap<>();
        statuses.put(Fixtures.behavioralOpenerKey(), TopicStatus.NOT_TESTED); // current
        statuses.put(Fixtures.coreDatabaseKey(),     TopicStatus.STRONG);
        statuses.put(Fixtures.coreApiKey(),          TopicStatus.STRONG);

        var inputs = Fixtures.inputsBuilder(fx)
                .allTopicStatuses(statuses)
                .verdict(verdict(7.0f, SignalStrength.STRONG, Correctness.CORRECT, Depth.DEEP,
                        Completeness.COMPLETE, HireSignal.yes, Grade.B, List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.decision()).isInstanceOf(PlannerDecision.EndSession.class);
        var end = (PlannerDecision.EndSession) out.decision();
        assertThat(end.reason()).isEqualTo(PlannerDecision.EndSession.EndReason.COVERAGE_COMPLETE);
    }

    // ── End session: question budget exhausted ───────────────────────────────

    @Test
    void endsSession_whenQuestionBudgetExhausted() {
        var fx = Fixtures.aDefaultBlueprint();
        var inputs = Fixtures.inputsBuilder(fx)
                .currentSessionQuestionCount(8) // == question_budget in fixtures
                .verdict(verdict(7.0f, SignalStrength.STRONG, Correctness.CORRECT, Depth.DEEP,
                        Completeness.COMPLETE, HireSignal.yes, Grade.B, List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        assertThat(out.decision()).isInstanceOf(PlannerDecision.EndSession.class);
        var end = (PlannerDecision.EndSession) out.decision();
        assertThat(end.reason()).isEqualTo(PlannerDecision.EndSession.EndReason.QUESTION_BUDGET_EXHAUSTED);
    }

    // ── Topic ordering: HIGH importance picked before MED ────────────────────

    @Test
    void nextTopicPicker_prefersHighImportance_thenLowerOrderHint() {
        var fx = Fixtures.aDefaultBlueprint();
        // Fixtures puts the BEHAVIORAL opener at order_hint=1, MED — current.
        // Both DOMAIN topics are HIGH; api at order_hint=3 < database at order_hint=4.
        var inputs = Fixtures.inputsBuilder(fx)
                .verdict(verdict(7.0f, SignalStrength.STRONG, Correctness.CORRECT, Depth.DEEP,
                        Completeness.COMPLETE, HireSignal.yes, Grade.B, List.of(), List.of()))
                .build();

        var out = planner.plan(inputs);

        var move = (PlannerDecision.MoveToNextTopic) out.decision();
        assertThat(move.nextTopic().getTopicValue()).isEqualTo("API_DESIGN");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static AssessmentVerdict verdict(
            Float score, SignalStrength ss, Correctness c, Depth d, Completeness comp,
            HireSignal hs, Grade g, List<WeakTarget> weak, List<StrongTarget> strong) {
        return new AssessmentVerdict(score, hs, g, ss, comp, c, d, weak, strong);
    }

    /**
     * Centralised fixture builder. Each test composes by tweaking only the
     * pieces it cares about — keeps assertions focused on the rule under
     * test instead of restating the whole input.
     */
    static class Fixtures {

        static InterviewBlueprint aDefaultBlueprint() {
            BlueprintTopic openerBeh = BlueprintTopic.builder()
                    .kind(TopicKind.COMPETENCY).topicValue("OWNERSHIP")
                    .importance(Importance.MED).targetDifficulty(Difficulty.MEDIUM)
                    .orderHint(1).build();
            BlueprintTopic apiCore = BlueprintTopic.builder()
                    .kind(TopicKind.DOMAIN).topicValue("API_DESIGN")
                    .importance(Importance.HIGH).targetDifficulty(Difficulty.MEDIUM)
                    .orderHint(3).build();
            BlueprintTopic dbCore = BlueprintTopic.builder()
                    .kind(TopicKind.DOMAIN).topicValue("DATABASE")
                    .importance(Importance.HIGH).targetDifficulty(Difficulty.MEDIUM)
                    .orderHint(4).build();

            return InterviewBlueprint.builder()
                    .id(UUID.randomUUID())
                    .targetRole("BACKEND").level("mid")
                    .interviewType(com.mockwise.interview.entity.enums.InterviewType.MIXED)
                    .topics(new java.util.ArrayList<>(List.of(openerBeh, apiCore, dbCore)))
                    .questionBudget(8)
                    .maxFollowUpsPerTopic(2)
                    .maxFollowUpsPerSession(4)
                    .timeBudgetMinutes(45)
                    .build();
        }

        static String behavioralOpenerKey() { return "COMPETENCY:OWNERSHIP"; }
        static String coreApiKey()          { return "DOMAIN:API_DESIGN"; }
        static String coreDatabaseKey()     { return "DOMAIN:DATABASE"; }

        static InterviewSession.InterviewSessionBuilder sessionBuilder() {
            return InterviewSession.builder()
                    .id(SESSION_ID)
                    .userId("user-1")
                    .targetRole("BACKEND").level("mid")
                    .status(SessionStatus.IN_PROGRESS)
                    .questionCount(8)
                    .consecutiveUnknownCount(0)
                    .runningStrongCount(0)
                    .globalDifficultyOffset(0)
                    .stretchMode(false)
                    .totalFollowUpsUsed(0);
        }

        static SessionTopicState.SessionTopicStateBuilder topicStateBuilder(InterviewBlueprint bp) {
            BlueprintTopic current = bp.getTopics().get(0); // the opener
            return SessionTopicState.builder()
                    .id(new SessionTopicStateId(SESSION_ID, current.getKind(), current.getTopicValue()))
                    .status(TopicStatus.PROBING)
                    .importance(current.getImportance())
                    .targetDifficulty(current.getTargetDifficulty())
                    .questionsAsked(1)
                    .followUpsUsed(0);
        }

        static InputBuilder inputsBuilder(InterviewBlueprint bp) {
            BlueprintTopic current = bp.getTopics().get(0);
            return new InputBuilder(bp, current);
        }

        /** Two-stage builder so callers can override session / topic state piecemeal. */
        static class InputBuilder {
            private final InterviewBlueprint blueprint;
            private final BlueprintTopic currentTopicConfig;
            private InterviewSession session = sessionBuilder().build();
            private SessionTopicState topicState;
            private AssessmentVerdict verdict;
            private Map<String, TopicStatus> allTopicStatuses;
            private int currentSessionQuestionCount = 1;

            InputBuilder(InterviewBlueprint bp, BlueprintTopic current) {
                this.blueprint = bp;
                this.currentTopicConfig = current;
                this.topicState = topicStateBuilder(bp).build();
                this.allTopicStatuses = defaultAllStatuses(bp);
            }

            private static Map<String, TopicStatus> defaultAllStatuses(InterviewBlueprint bp) {
                Map<String, TopicStatus> m = new LinkedHashMap<>();
                for (BlueprintTopic t : bp.getTopics()) {
                    m.put(PlannerInputs.topicKey(t), TopicStatus.NOT_TESTED);
                }
                return m;
            }

            InputBuilder session(InterviewSession s) { this.session = s; return this; }
            InputBuilder topicState(SessionTopicState ts) { this.topicState = ts; return this; }
            InputBuilder verdict(AssessmentVerdict v) { this.verdict = v; return this; }
            InputBuilder allTopicStatuses(Map<String, TopicStatus> m) { this.allTopicStatuses = m; return this; }
            InputBuilder currentSessionQuestionCount(int n) { this.currentSessionQuestionCount = n; return this; }

            PlannerInputs build() {
                return new PlannerInputs(
                        session, blueprint, topicState, currentTopicConfig,
                        verdict, allTopicStatuses, currentSessionQuestionCount);
            }
        }
    }
}
