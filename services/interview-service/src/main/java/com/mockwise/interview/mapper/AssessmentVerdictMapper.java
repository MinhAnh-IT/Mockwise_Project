package com.mockwise.interview.mapper;

import com.mockwise.interview.dto.assessment.AssessmentVerdict;
import com.mockwise.interview.dto.assessment.StrongTarget;
import com.mockwise.interview.dto.assessment.WeakTarget;
import com.mockwise.interview.dto.assessment.input.BehavioralEvalOutput;
import com.mockwise.interview.dto.assessment.input.ConceptualEvalOutput;
import com.mockwise.interview.dto.assessment.input.LiveCodingEvalOutput;
import com.mockwise.interview.enums.Completeness;
import com.mockwise.interview.enums.Correctness;
import com.mockwise.interview.enums.Depth;
import com.mockwise.interview.enums.Severity;
import com.mockwise.interview.enums.SignalStrength;
import com.mockwise.interview.enums.StrongTargetKind;
import com.mockwise.interview.enums.WeakTargetKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps a raw AI evaluator output (one of three shapes) into the canonical
 * {@link AssessmentVerdict} the planner consumes.
 *
 * <p>Every threshold here is a deliberate policy decision documented inline.
 * Changing them changes which Case (A/B/C/D) the planner falls into for
 * borderline scores — keep these constants visible and reviewed.
 *
 * <p>Notes on null-tolerance:
 * <ul>
 *   <li>Completeness comes straight from the AI output and is required by
 *       the new schema (commit {@code 26262ff} on the AI side). If a
 *       legacy payload omits it we default to COMPLETE rather than
 *       throwing — the orchestrator can always re-derive from transcript
 *       length later if needed.</li>
 *   <li>Lists default to empty; never null in the returned verdict.</li>
 * </ul>
 */
@Component
public class AssessmentVerdictMapper {

    // ── Thresholds ───────────────────────────────────────────────────────────
    // Centralised so a future calibration change is one edit, not a hunt.

    /** Below this fraction of detected signals/correct concepts → NONE. */
    private static final double SIGNAL_NONE_MAX = 0.0001;
    /** Detected ratio < this → PARTIAL. */
    private static final double SIGNAL_PARTIAL_MAX = 0.40;
    /** Detected ratio < this → ADEQUATE; ≥ this → STRONG. */
    private static final double SIGNAL_STRONG_MIN = 0.75;

    /** Per-dimension score < this → SURFACE. */
    private static final int DEPTH_SURFACE_MAX = 40;
    /** Per-dimension score < this → MODERATE; ≥ this → DEEP. */
    private static final int DEPTH_DEEP_MIN = 70;

    /** Conceptual accuracy score < this → WRONG (case A1: drop the topic). */
    private static final int CORE_ACCURACY_WRONG_MAX = 35;
    /** Conceptual accuracy score < this OR has misconceptions → MIXED. */
    private static final int CORE_ACCURACY_MIXED_MAX = 70;

    /** Live-coding test pass rate ≥ this AND is_optimal → CORRECT. */
    private static final double CODE_PASS_RATE_CORRECT = 0.90;
    /** Live-coding test pass rate < this → WRONG. */
    private static final double CODE_PASS_RATE_WRONG = 0.30;

    /** Code-issue types that always escalate severity to HIGH. */
    private static final List<String> HIGH_SEVERITY_CODE_ISSUES = List.of(
            "logic_error", "wrong_complexity", "missing_edge_case"
    );

    // ── Public API ───────────────────────────────────────────────────────────

    public AssessmentVerdict fromBehavioral(BehavioralEvalOutput out) {
        List<WeakTarget> weak = new ArrayList<>();
        List<StrongTarget> strong = new ArrayList<>();

        // Signal coverage drives both signal_strength and the weak/strong
        // target lists. Treating an undetected signal as a HIGH-severity
        // weakness is correct here: BEHAVIORAL signals are the *whole*
        // measurement of the question — missing one is missing the point.
        int totalSignals = 0;
        int detectedSignals = 0;
        if (out.signalCoverage() != null) {
            for (var s : out.signalCoverage()) {
                totalSignals++;
                if (s.detected()) {
                    detectedSignals++;
                    strong.add(new StrongTarget(StrongTargetKind.SIGNAL, s.signalName()));
                } else {
                    weak.add(new WeakTarget(WeakTargetKind.SIGNAL, s.signalName(), Severity.HIGH));
                }
            }
        }
        SignalStrength signalStrength = strengthFromRatio(detectedSignals, totalSignals);

        // Red flags become weak targets too. AI severity strings are
        // lowercase ("low" / "medium" / "high"); we normalise into our enum.
        if (out.redFlags() != null) {
            for (var f : out.redFlags()) {
                Severity sev = parseSeverity(f.severity());
                weak.add(new WeakTarget(WeakTargetKind.RED_FLAG, f.type(), sev));
            }
        }

        // Behavioral correctness: rarely WRONG (story-telling questions
        // rarely have a "wrong answer" the way conceptual ones do). MIXED
        // when there is at least one HIGH-severity red flag (vague_action,
        // missing_result, blame_shifting, hypothetical) — those are
        // structural failures, not stylistic ones.
        boolean hasHighRedFlag = out.redFlags() != null
                && out.redFlags().stream().anyMatch(f -> "high".equalsIgnoreCase(f.severity()));
        Correctness correctness = hasHighRedFlag ? Correctness.MIXED : Correctness.CORRECT;

        // Behavioral depth proxy: specificity dimension. A behavioural
        // answer is "deep" when the candidate names specific people,
        // numbers, dates — exactly what specificity measures.
        int specificityScore = out.scores() != null && out.scores().specificity() != null
                ? out.scores().specificity().score() : 0;
        Depth depth = depthFromScore(specificityScore);

        return new AssessmentVerdict(
                normaliseScore(out.overallScore()),
                out.summary() != null ? out.summary().hireSignal() : null,
                out.summary() != null ? out.summary().grade() : null,
                signalStrength,
                completenessOrDefault(out.completeness()),
                correctness,
                depth,
                weak,
                strong
        );
    }

    public AssessmentVerdict fromConceptual(ConceptualEvalOutput out) {
        List<WeakTarget> weak = new ArrayList<>();
        List<StrongTarget> strong = new ArrayList<>();

        // Concept coverage rules:
        //   mentioned=false              → HIGH-severity weak target (gap)
        //   mentioned=true, correct=false → HIGH-severity weak target (worse than gap)
        //   mentioned=true, correct=true  → strong target
        //   mentioned=true, correct=null  → ignore (not classifiable)
        // The "wrong is worse than missing" framing is from the conceptual
        // prompt itself — a candidate who states something incorrectly is
        // demonstrating active misunderstanding, not just a blind spot.
        int totalConcepts = 0;
        int strongConcepts = 0;
        if (out.conceptCoverage() != null) {
            for (var c : out.conceptCoverage()) {
                totalConcepts++;
                if (!c.mentioned()) {
                    weak.add(new WeakTarget(WeakTargetKind.CONCEPT, c.conceptName(), Severity.HIGH));
                } else if (Boolean.FALSE.equals(c.correct())) {
                    weak.add(new WeakTarget(WeakTargetKind.CONCEPT, c.conceptName(), Severity.HIGH));
                } else if (Boolean.TRUE.equals(c.correct())) {
                    strongConcepts++;
                    strong.add(new StrongTarget(StrongTargetKind.CONCEPT, c.conceptName()));
                }
                // mentioned=true, correct=null → not counted either way.
            }
        }
        SignalStrength signalStrength = strengthFromRatio(strongConcepts, totalConcepts);

        // Misconceptions are always HIGH severity and use the claim itself
        // as the value — truncated so it fits the wire field length.
        if (out.misconceptions() != null) {
            for (var m : out.misconceptions()) {
                String value = truncate(m.claim(), 200);
                weak.add(new WeakTarget(WeakTargetKind.MISCONCEPTION, value, Severity.HIGH));
            }
        }

        int accuracyScore = out.scores() != null && out.scores().accuracy() != null
                ? out.scores().accuracy().score() : 0;
        boolean hasMisconceptions = out.misconceptions() != null && !out.misconceptions().isEmpty();
        Correctness correctness;
        if (accuracyScore < CORE_ACCURACY_WRONG_MAX) {
            correctness = Correctness.WRONG;
        } else if (hasMisconceptions || accuracyScore < CORE_ACCURACY_MIXED_MAX) {
            correctness = Correctness.MIXED;
        } else {
            correctness = Correctness.CORRECT;
        }

        // Conceptual depth: directly from the dedicated `depth` dimension
        // (the AI prompt explicitly asks for depth relative to depth_expected).
        int depthScore = out.scores() != null && out.scores().depth() != null
                ? out.scores().depth().score() : 0;
        Depth depth = depthFromScore(depthScore);

        return new AssessmentVerdict(
                normaliseScore(out.overallScore()),
                out.summary() != null ? out.summary().hireSignal() : null,
                out.summary() != null ? out.summary().grade() : null,
                signalStrength,
                completenessOrDefault(out.completeness()),
                correctness,
                depth,
                weak,
                strong
        );
    }

    public AssessmentVerdict fromLiveCoding(LiveCodingEvalOutput out) {
        List<WeakTarget> weak = new ArrayList<>();
        List<StrongTarget> strong = new ArrayList<>(); // coding has no equivalent of named signals

        // LIVE_CODING has no expected_signals/key_concepts shape, so
        // signal_strength derives from overall_score directly: a passing
        // solution proves the "signal" (ability to solve the problem)
        // strongly; a low-scoring one proves it weakly.
        SignalStrength signalStrength = liveCodingStrength(out.overallScore());

        // Code issues map to RED_FLAG weak targets. Some issue types are
        // structural (logic_error, wrong_complexity) — those are HIGH no
        // matter what; everything else (naming, style, magic_number) is MED.
        if (out.analysis() != null && out.analysis().codeIssues() != null) {
            for (var issue : out.analysis().codeIssues()) {
                Severity sev = HIGH_SEVERITY_CODE_ISSUES.contains(issue.type())
                        ? Severity.HIGH : Severity.MED;
                weak.add(new WeakTarget(WeakTargetKind.RED_FLAG, issue.type(), sev));
            }
        }

        // Correctness for coding: compute from is_optimal + the
        // problem_solving score as a proxy for test pass rate (the eval
        // payload doesn't carry the raw rate; the dimension does encode
        // it indirectly via the prompt's "cap at 40 if pass < 50%" rule).
        boolean isOptimal = out.analysis() != null && out.analysis().isOptimal();
        int problemSolvingScore = out.scores() != null && out.scores().problemSolving() != null
                ? out.scores().problemSolving().score() : 0;
        double passRateProxy = problemSolvingScore / 100.0;
        Correctness correctness;
        if (isOptimal && passRateProxy >= CODE_PASS_RATE_CORRECT) {
            correctness = Correctness.CORRECT;
        } else if (passRateProxy < CODE_PASS_RATE_WRONG) {
            correctness = Correctness.WRONG;
        } else {
            correctness = Correctness.MIXED;
        }

        // Coding depth: is_optimal is the strongest signal; below that
        // fall back to code_quality.
        int codeQualityScore = out.scores() != null && out.scores().codeQuality() != null
                ? out.scores().codeQuality().score() : 0;
        Depth depth;
        if (isOptimal) {
            depth = Depth.DEEP;
        } else if (codeQualityScore >= DEPTH_DEEP_MIN) {
            depth = Depth.MODERATE;
        } else if (codeQualityScore >= DEPTH_SURFACE_MAX) {
            depth = Depth.MODERATE;
        } else {
            depth = Depth.SURFACE;
        }

        return new AssessmentVerdict(
                normaliseScore(out.overallScore()),
                out.summary() != null ? out.summary().hireSignal() : null,
                out.summary() != null ? out.summary().grade() : null,
                signalStrength,
                completenessOrDefault(out.completeness()),
                correctness,
                depth,
                weak,
                strong
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Float normaliseScore(int aiScore) {
        return aiScore / 10.0f;
    }

    private static SignalStrength strengthFromRatio(int hits, int total) {
        if (total == 0) return SignalStrength.NONE;
        double ratio = (double) hits / total;
        if (ratio <= SIGNAL_NONE_MAX) return SignalStrength.NONE;
        if (ratio < SIGNAL_PARTIAL_MAX) return SignalStrength.PARTIAL;
        if (ratio < SIGNAL_STRONG_MIN) return SignalStrength.ADEQUATE;
        return SignalStrength.STRONG;
    }

    private static SignalStrength liveCodingStrength(int overallScore) {
        if (overallScore >= 75) return SignalStrength.STRONG;
        if (overallScore >= 60) return SignalStrength.ADEQUATE;
        if (overallScore >= 35) return SignalStrength.PARTIAL;
        return SignalStrength.NONE;
    }

    private static Depth depthFromScore(int score) {
        if (score < DEPTH_SURFACE_MAX) return Depth.SURFACE;
        if (score < DEPTH_DEEP_MIN) return Depth.MODERATE;
        return Depth.DEEP;
    }

    private static Severity parseSeverity(String raw) {
        if (raw == null) return Severity.MED;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "high"   -> Severity.HIGH;
            case "low"    -> Severity.LOW;
            default       -> Severity.MED;   // includes "medium"
        };
    }

    private static Completeness completenessOrDefault(Completeness c) {
        return c != null ? c : Completeness.COMPLETE;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
