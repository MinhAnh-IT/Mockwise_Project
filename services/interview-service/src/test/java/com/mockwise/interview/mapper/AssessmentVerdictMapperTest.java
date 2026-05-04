package com.mockwise.interview.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.mockwise.interview.enums.Completeness;
import com.mockwise.interview.enums.Correctness;
import com.mockwise.interview.enums.Depth;
import com.mockwise.interview.enums.Grade;
import com.mockwise.interview.enums.HireSignal;
import com.mockwise.interview.enums.Severity;
import com.mockwise.interview.enums.SignalStrength;
import com.mockwise.interview.enums.StrongTargetKind;
import com.mockwise.interview.enums.WeakTargetKind;
import com.mockwise.interview.dto.assessment.StrongTarget;
import com.mockwise.interview.dto.assessment.WeakTarget;
import com.mockwise.interview.dto.assessment.input.BehavioralEvalOutput;
import com.mockwise.interview.dto.assessment.input.ConceptualEvalOutput;
import com.mockwise.interview.dto.assessment.input.LiveCodingEvalOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Verifies the mapper preserves the contract documented in
 * docs/question-selection-design.md §4. Each test starts from a JSON
 * payload shaped exactly like the AI service's output (snake_case keys),
 * deserialises with the same Jackson config the production code uses,
 * and asserts on the derived verdict.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Happy path for each of the 3 evaluator types.</li>
 *   <li>NO_ANSWER passes through (Case B trigger).</li>
 *   <li>All-detected behavioral signals → STRONG, no weak targets.</li>
 *   <li>Conceptual misconception promotes correctness from CORRECT to MIXED.</li>
 *   <li>Coding logic_error escalates to HIGH severity.</li>
 *   <li>Empty/missing optional collections are tolerated.</li>
 * </ul>
 */
class AssessmentVerdictMapperTest {

    private static ObjectMapper mapper;
    private final AssessmentVerdictMapper subject = new AssessmentVerdictMapper();

    @BeforeAll
    static void setupMapper() {
        // Mirror the production Spring Boot config: snake_case property
        // binding so JSON keys like "overall_score" land on `overallScore`.
        mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    // ── BEHAVIORAL ───────────────────────────────────────────────────────────

    @Test
    void behavioral_strongAnswer_yieldsStrongSignals_noWeakTargets() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "behavioral",
              "overall_score": 88,
              "completeness": "COMPLETE",
              "scores": {
                "star_structure":  {"score": 90, "max": 100, "weight": 0.25, "note": ""},
                "relevance":       {"score": 85, "max": 100, "weight": 0.20, "note": ""},
                "specificity":     {"score": 82, "max": 100, "weight": 0.25, "note": ""},
                "impact_result":   {"score": 88, "max": 100, "weight": 0.20, "note": ""},
                "self_awareness":  {"score": 85, "max": 100, "weight": 0.10, "note": ""}
              },
              "signal_coverage": [
                {"signal_name": "specific_recent_example",  "detected": true},
                {"signal_name": "fact_based_argument",      "detected": true},
                {"signal_name": "names_own_role_clearly",   "detected": true},
                {"signal_name": "clear_resolution",         "detected": true}
              ],
              "red_flags": [],
              "summary": {"grade": "B", "hire_signal": "yes", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, BehavioralEvalOutput.class);

        var verdict = subject.fromBehavioral(input);

        assertThat(verdict.scoreNormalized()).isEqualTo(8.8f);
        assertThat(verdict.grade()).isEqualTo(Grade.B);
        assertThat(verdict.hireSignal()).isEqualTo(HireSignal.yes);
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.STRONG);
        assertThat(verdict.completeness()).isEqualTo(Completeness.COMPLETE);
        assertThat(verdict.correctness()).isEqualTo(Correctness.CORRECT);
        // specificity 82 → DEEP
        assertThat(verdict.depth()).isEqualTo(Depth.DEEP);
        assertThat(verdict.weakTargets()).isEmpty();
        assertThat(verdict.strongTargets()).hasSize(4);
        assertThat(verdict.strongTargets()).allSatisfy(t ->
                assertThat(t.kind()).isEqualTo(StrongTargetKind.SIGNAL));
    }

    @Test
    void behavioral_partialSignals_yieldsAdequate_andHighRedFlagFlipsCorrectness() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "behavioral",
              "overall_score": 55,
              "completeness": "INCOMPLETE",
              "scores": {
                "star_structure":  {"score": 50, "max": 100, "weight": 0.25, "note": ""},
                "relevance":       {"score": 70, "max": 100, "weight": 0.20, "note": ""},
                "specificity":     {"score": 35, "max": 100, "weight": 0.25, "note": ""},
                "impact_result":   {"score": 40, "max": 100, "weight": 0.20, "note": ""},
                "self_awareness":  {"score": 60, "max": 100, "weight": 0.10, "note": ""}
              },
              "signal_coverage": [
                {"signal_name": "specific_recent_example", "detected": false},
                {"signal_name": "fact_based_argument",     "detected": true},
                {"signal_name": "clear_resolution",        "detected": true},
                {"signal_name": "names_own_role_clearly",  "detected": false}
              ],
              "red_flags": [
                {"type": "missing_result", "severity": "high", "detail": "..."},
                {"type": "vague_action",   "severity": "medium", "detail": "..."}
              ],
              "summary": {"grade": "D", "hire_signal": "no", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, BehavioralEvalOutput.class);

        var verdict = subject.fromBehavioral(input);

        // 2/4 detected = 0.5 → ADEQUATE (between 0.40 and 0.75)
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.ADEQUATE);
        // specificity 35 → SURFACE (< 40)
        assertThat(verdict.depth()).isEqualTo(Depth.SURFACE);
        // HIGH-severity red flag flips to MIXED
        assertThat(verdict.correctness()).isEqualTo(Correctness.MIXED);
        assertThat(verdict.completeness()).isEqualTo(Completeness.INCOMPLETE);

        // 2 missing signals + 2 red flags = 4 weak targets
        assertThat(verdict.weakTargets()).hasSize(4);
        assertThat(verdict.weakTargets())
                .extracting(WeakTarget::kind, WeakTarget::value, WeakTarget::severity)
                .containsExactlyInAnyOrder(
                        tuple(WeakTargetKind.SIGNAL, "specific_recent_example", Severity.HIGH),
                        tuple(WeakTargetKind.SIGNAL, "names_own_role_clearly",  Severity.HIGH),
                        tuple(WeakTargetKind.RED_FLAG, "missing_result",        Severity.HIGH),
                        tuple(WeakTargetKind.RED_FLAG, "vague_action",          Severity.MED));
    }

    @Test
    void behavioral_noAnswer_passesThroughCompleteness() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "behavioral",
              "overall_score": 0,
              "completeness": "NO_ANSWER",
              "scores": {
                "star_structure":  {"score": 0, "max": 100, "weight": 0.25, "note": ""},
                "relevance":       {"score": 0, "max": 100, "weight": 0.20, "note": ""},
                "specificity":     {"score": 0, "max": 100, "weight": 0.25, "note": ""},
                "impact_result":   {"score": 0, "max": 100, "weight": 0.20, "note": ""},
                "self_awareness":  {"score": 0, "max": 100, "weight": 0.10, "note": ""}
              },
              "signal_coverage": [],
              "red_flags": [],
              "summary": {"grade": "F", "hire_signal": "strong_no", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, BehavioralEvalOutput.class);

        var verdict = subject.fromBehavioral(input);

        assertThat(verdict.completeness()).isEqualTo(Completeness.NO_ANSWER);
        // 0 of 0 signals → NONE (not divide-by-zero)
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.NONE);
        assertThat(verdict.weakTargets()).isEmpty();
        assertThat(verdict.strongTargets()).isEmpty();
        assertThat(verdict.hireSignal()).isEqualTo(HireSignal.strong_no);
    }

    // ── CONCEPTUAL ───────────────────────────────────────────────────────────

    @Test
    void conceptual_strongAnswer_allConceptsCovered() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "core_conceptual",
              "overall_score": 86,
              "completeness": "COMPLETE",
              "scores": {
                "accuracy":              {"score": 92, "max": 100, "weight": 0.35, "note": ""},
                "depth":                 {"score": 80, "max": 100, "weight": 0.30, "note": ""},
                "practical_application": {"score": 85, "max": 100, "weight": 0.20, "note": ""},
                "clarity":               {"score": 90, "max": 100, "weight": 0.15, "note": ""}
              },
              "concept_coverage": [
                {"concept_name": "btree_basics",       "mentioned": true,  "correct": true},
                {"concept_name": "index_scan",         "mentioned": true,  "correct": true},
                {"concept_name": "write_overhead",     "mentioned": true,  "correct": true},
                {"concept_name": "composite_ordering", "mentioned": true,  "correct": true}
              ],
              "misconceptions": [],
              "summary": {"grade": "B", "hire_signal": "yes", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, ConceptualEvalOutput.class);

        var verdict = subject.fromConceptual(input);

        assertThat(verdict.scoreNormalized()).isEqualTo(8.6f);
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.STRONG);
        assertThat(verdict.correctness()).isEqualTo(Correctness.CORRECT);
        assertThat(verdict.depth()).isEqualTo(Depth.DEEP);
        assertThat(verdict.weakTargets()).isEmpty();
        assertThat(verdict.strongTargets()).hasSize(4);
    }

    @Test
    void conceptual_misconception_flipsCorrectnessToMixed() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "core_conceptual",
              "overall_score": 70,
              "completeness": "COMPLETE",
              "scores": {
                "accuracy":              {"score": 78, "max": 100, "weight": 0.35, "note": ""},
                "depth":                 {"score": 60, "max": 100, "weight": 0.30, "note": ""},
                "practical_application": {"score": 70, "max": 100, "weight": 0.20, "note": ""},
                "clarity":               {"score": 80, "max": 100, "weight": 0.15, "note": ""}
              },
              "concept_coverage": [
                {"concept_name": "btree_basics",       "mentioned": true,  "correct": true},
                {"concept_name": "composite_ordering", "mentioned": false, "correct": null},
                {"concept_name": "covering_index",     "mentioned": true,  "correct": false}
              ],
              "misconceptions": [
                {"claim": "indexes always speed up queries", "correction": "..."}
              ],
              "summary": {"grade": "C", "hire_signal": "weak_yes", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, ConceptualEvalOutput.class);

        var verdict = subject.fromConceptual(input);

        // Even with accuracy 78 (≥ 70 = CORRECT range), the misconception
        // alone should drag this to MIXED — that's the policy point.
        assertThat(verdict.correctness()).isEqualTo(Correctness.MIXED);
        // 1/3 strongConcepts = 0.33 → PARTIAL
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.PARTIAL);
        // depth 60 → MODERATE (40 ≤ score < 70)
        assertThat(verdict.depth()).isEqualTo(Depth.MODERATE);

        assertThat(verdict.weakTargets())
                .extracting(WeakTarget::kind, WeakTarget::value)
                .containsExactlyInAnyOrder(
                        tuple(WeakTargetKind.CONCEPT, "composite_ordering"),       // missing
                        tuple(WeakTargetKind.CONCEPT, "covering_index"),           // wrong
                        tuple(WeakTargetKind.MISCONCEPTION, "indexes always speed up queries"));
        assertThat(verdict.strongTargets())
                .extracting(StrongTarget::value)
                .containsExactly("btree_basics");
    }

    @Test
    void conceptual_lowAccuracy_yieldsWrong() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "core_conceptual",
              "overall_score": 22,
              "completeness": "COMPLETE",
              "scores": {
                "accuracy":              {"score": 25, "max": 100, "weight": 0.35, "note": ""},
                "depth":                 {"score": 20, "max": 100, "weight": 0.30, "note": ""},
                "practical_application": {"score": 15, "max": 100, "weight": 0.20, "note": ""},
                "clarity":               {"score": 30, "max": 100, "weight": 0.15, "note": ""}
              },
              "concept_coverage": [
                {"concept_name": "btree_basics", "mentioned": false, "correct": null}
              ],
              "misconceptions": [],
              "summary": {"grade": "F", "hire_signal": "strong_no", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, ConceptualEvalOutput.class);

        var verdict = subject.fromConceptual(input);

        // accuracy 25 < 35 → WRONG (Case A1 driver)
        assertThat(verdict.correctness()).isEqualTo(Correctness.WRONG);
        assertThat(verdict.depth()).isEqualTo(Depth.SURFACE);
    }

    // ── LIVE CODING ──────────────────────────────────────────────────────────

    @Test
    void liveCoding_optimalSolution_yieldsCorrectAndDeep() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "live_coding",
              "overall_score": 92,
              "completeness": "COMPLETE",
              "scores": {
                "time_complexity":  {"score": 100, "max": 100, "weight": 0.30, "note": ""},
                "space_complexity": {"score":  90, "max": 100, "weight": 0.15, "note": ""},
                "code_quality":     {"score":  85, "max": 100, "weight": 0.35, "note": ""},
                "problem_solving":  {"score":  95, "max": 100, "weight": 0.20, "note": ""}
              },
              "analysis": {
                "is_optimal": true,
                "code_issues": []
              },
              "summary": {"grade": "A", "hire_signal": "strong_yes", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, LiveCodingEvalOutput.class);

        var verdict = subject.fromLiveCoding(input);

        assertThat(verdict.scoreNormalized()).isEqualTo(9.2f);
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.STRONG);
        assertThat(verdict.correctness()).isEqualTo(Correctness.CORRECT);
        assertThat(verdict.depth()).isEqualTo(Depth.DEEP);
        assertThat(verdict.weakTargets()).isEmpty();
    }

    @Test
    void liveCoding_logicError_escalatesToHighSeverity() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "live_coding",
              "overall_score": 45,
              "completeness": "COMPLETE",
              "scores": {
                "time_complexity":  {"score": 50, "max": 100, "weight": 0.30, "note": ""},
                "space_complexity": {"score": 60, "max": 100, "weight": 0.15, "note": ""},
                "code_quality":     {"score": 35, "max": 100, "weight": 0.35, "note": ""},
                "problem_solving":  {"score": 40, "max": 100, "weight": 0.20, "note": ""}
              },
              "analysis": {
                "is_optimal": false,
                "code_issues": [
                  {"type": "logic_error",      "detail": "off-by-one in loop"},
                  {"type": "naming",           "detail": "x is unclear"},
                  {"type": "wrong_complexity", "detail": "O(n^2) when O(n) possible"}
                ]
              },
              "summary": {"grade": "D", "hire_signal": "no", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, LiveCodingEvalOutput.class);

        var verdict = subject.fromLiveCoding(input);

        // overall 45 → PARTIAL (35 ≤ score < 60)
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.PARTIAL);
        // problem_solving 40 = 0.40 pass-rate proxy → MIXED (between WRONG 0.30 and CORRECT 0.90)
        assertThat(verdict.correctness()).isEqualTo(Correctness.MIXED);

        assertThat(verdict.weakTargets())
                .extracting(WeakTarget::kind, WeakTarget::value, WeakTarget::severity)
                .containsExactlyInAnyOrder(
                        tuple(WeakTargetKind.RED_FLAG, "logic_error",      Severity.HIGH),
                        tuple(WeakTargetKind.RED_FLAG, "wrong_complexity", Severity.HIGH),
                        tuple(WeakTargetKind.RED_FLAG, "naming",           Severity.MED));
    }

    @Test
    void liveCoding_emptySubmission_yieldsNoAnswer() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "live_coding",
              "overall_score": 0,
              "completeness": "NO_ANSWER",
              "scores": {
                "time_complexity":  {"score": 0, "max": 100, "weight": 0.30, "note": ""},
                "space_complexity": {"score": 0, "max": 100, "weight": 0.15, "note": ""},
                "code_quality":     {"score": 0, "max": 100, "weight": 0.35, "note": ""},
                "problem_solving":  {"score": 0, "max": 100, "weight": 0.20, "note": ""}
              },
              "analysis": {"is_optimal": false, "code_issues": []},
              "summary": {"grade": "F", "hire_signal": "strong_no", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, LiveCodingEvalOutput.class);

        var verdict = subject.fromLiveCoding(input);

        assertThat(verdict.completeness()).isEqualTo(Completeness.NO_ANSWER);
        assertThat(verdict.signalStrength()).isEqualTo(SignalStrength.NONE);
        // problem_solving 0 → 0.00 pass-rate proxy → WRONG (< 0.30)
        assertThat(verdict.correctness()).isEqualTo(Correctness.WRONG);
        assertThat(verdict.depth()).isEqualTo(Depth.SURFACE);
    }

    // ── Robustness ───────────────────────────────────────────────────────────

    @Test
    void completeness_missing_defaultsToComplete() throws Exception {
        // Legacy payload (or older AI build) without the completeness field.
        String json = """
            {
              "session_id": "sess",
              "interview_type": "behavioral",
              "overall_score": 70,
              "scores": {
                "star_structure":  {"score": 70, "max": 100, "weight": 0.25, "note": ""},
                "relevance":       {"score": 70, "max": 100, "weight": 0.20, "note": ""},
                "specificity":     {"score": 70, "max": 100, "weight": 0.25, "note": ""},
                "impact_result":   {"score": 70, "max": 100, "weight": 0.20, "note": ""},
                "self_awareness":  {"score": 70, "max": 100, "weight": 0.10, "note": ""}
              },
              "signal_coverage": [{"signal_name": "x", "detected": true}],
              "red_flags": [],
              "summary": {"grade": "C", "hire_signal": "weak_yes", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, BehavioralEvalOutput.class);

        var verdict = subject.fromBehavioral(input);

        // Defaulted, not null, not throwing.
        assertThat(verdict.completeness()).isEqualTo(Completeness.COMPLETE);
    }

    @Test
    void unknownAiSeverityString_defaultsToMed() throws Exception {
        String json = """
            {
              "session_id": "sess",
              "interview_type": "behavioral",
              "overall_score": 50,
              "completeness": "INCOMPLETE",
              "scores": {
                "star_structure":  {"score": 50, "max": 100, "weight": 0.25, "note": ""},
                "relevance":       {"score": 50, "max": 100, "weight": 0.20, "note": ""},
                "specificity":     {"score": 50, "max": 100, "weight": 0.25, "note": ""},
                "impact_result":   {"score": 50, "max": 100, "weight": 0.20, "note": ""},
                "self_awareness":  {"score": 50, "max": 100, "weight": 0.10, "note": ""}
              },
              "signal_coverage": [],
              "red_flags": [{"type": "future_unknown_type", "severity": "moderate", "detail": ""}],
              "summary": {"grade": "D", "hire_signal": "no", "one_line_verdict": "..."}
            }
            """;
        var input = mapper.readValue(json, BehavioralEvalOutput.class);

        var verdict = subject.fromBehavioral(input);

        // "moderate" is not in our switch — should fall through to MED rather
        // than blowing up the consumer.
        assertThat(verdict.weakTargets()).hasSize(1);
        assertThat(verdict.weakTargets().get(0).severity()).isEqualTo(Severity.MED);
    }
}
