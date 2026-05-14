package com.mockwise.interview.dto.assessment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.dto.assessment.input.BehavioralEvalOutput;
import com.mockwise.interview.dto.assessment.input.ConceptualEvalOutput;
import com.mockwise.interview.dto.assessment.input.LiveCodingEvalOutput;
import com.mockwise.interview.enums.Completeness;
import com.mockwise.interview.enums.QuestionType;

import java.util.List;
import java.util.Map;

/**
 * User-facing projection of the AI service's raw evaluation payload —
 * what the report UI renders next to the consolidated {@link AssessmentVerdict}.
 *
 * <p>Three variants discriminated by {@code kind} (matches {@link QuestionType}
 * one-for-one) so the FE can render with a typed discriminated union instead
 * of the legacy free-form map.
 *
 * <p>{@code raw_evaluation} on the {@code answer} row keeps the full AI
 * payload (including planner-only / meta fields). This DTO is the curated
 * subset of fields that have a sensible presentation: per-dimension scores
 * + the note explaining them, signal / concept coverage with quoted
 * evidence, red flags with details, code issues with line numbers,
 * misconceptions with corrections, plus the strengths / improvements /
 * follow-up study guidance the AI generates.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "kind"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = EvaluationDetail.Behavioral.class, name = "BEHAVIORAL"),
        @JsonSubTypes.Type(value = EvaluationDetail.Conceptual.class, name = "CORE_CONCEPTUAL"),
        @JsonSubTypes.Type(value = EvaluationDetail.LiveCoding.class, name = "LIVE_CODING"),
})
public sealed interface EvaluationDetail
        permits EvaluationDetail.Behavioral,
                EvaluationDetail.Conceptual,
                EvaluationDetail.LiveCoding {

    /**
     * Convert the raw AI payload (as stored in {@code answer.raw_evaluation})
     * into the typed projection for the given question type. Returns null
     * when the payload is missing / empty / shaped wrong — callers are
     * expected to soft-fail (the verdict carries enough for the planner;
     * detail is purely cosmetic).
     */
    static EvaluationDetail fromRaw(
            QuestionType questionType,
            Map<String, Object> rawEvaluation,
            ObjectMapper objectMapper) {
        if (questionType == null || rawEvaluation == null || rawEvaluation.isEmpty()
                || objectMapper == null) {
            return null;
        }
        try {
            return switch (questionType) {
                case BEHAVIORAL -> Behavioral.from(
                        objectMapper.convertValue(rawEvaluation, BehavioralEvalOutput.class));
                case CORE_CONCEPTUAL -> Conceptual.from(
                        objectMapper.convertValue(rawEvaluation, ConceptualEvalOutput.class));
                case LIVE_CODING -> LiveCoding.from(
                        objectMapper.convertValue(rawEvaluation, LiveCodingEvalOutput.class));
            };
        } catch (IllegalArgumentException ex) {
            // Legacy / malformed payload — drop the detail rather than 500.
            return null;
        }
    }

    // ── Variants ─────────────────────────────────────────────────────────────

    record Behavioral(
            Integer overallScore,
            Completeness completeness,
            String oneLineVerdict,
            Scores scores,
            StarBreakdown starBreakdown,
            List<SignalItem> signalCoverage,
            List<RedFlagItem> redFlags,
            Feedback feedback
    ) implements EvaluationDetail {

        public record Scores(
                ScoreEntry starStructure,
                ScoreEntry relevance,
                ScoreEntry specificity,
                ScoreEntry impactResult,
                ScoreEntry selfAwareness
        ) {}

        public record StarBreakdown(
                StarComponentItem situation,
                StarComponentItem task,
                StarComponentItem action,
                StarComponentItem result
        ) {}

        public record StarComponentItem(boolean detected, String quality, String excerpt) {}

        public record SignalItem(String signalName, boolean detected, String evidence) {}

        public record RedFlagItem(String type, String severity, String detail) {}

        public record Feedback(
                List<String> strengths,
                List<String> improvements,
                String sampleStrongerAnswerStructure
        ) {}

        static Behavioral from(BehavioralEvalOutput o) {
            return new Behavioral(
                    o.overallScore(),
                    o.completeness(),
                    o.summary() == null ? null : o.summary().oneLineVerdict(),
                    o.scores() == null ? null : new Scores(
                            scoreEntryOf(o.scores().starStructure()),
                            scoreEntryOf(o.scores().relevance()),
                            scoreEntryOf(o.scores().specificity()),
                            scoreEntryOf(o.scores().impactResult()),
                            scoreEntryOf(o.scores().selfAwareness())),
                    starBreakdownOf(o.starBreakdown()),
                    o.signalCoverage() == null ? List.of()
                            : o.signalCoverage().stream()
                                    .map(s -> new SignalItem(s.signalName(), s.detected(), s.evidence()))
                                    .toList(),
                    o.redFlags() == null ? List.of()
                            : o.redFlags().stream()
                                    .map(f -> new RedFlagItem(f.type(), f.severity(), f.detail()))
                                    .toList(),
                    feedbackOf(o.feedback()));
        }

        private static StarBreakdown starBreakdownOf(BehavioralEvalOutput.StarBreakdown b) {
            if (b == null) return null;
            return new StarBreakdown(
                    starComponentOf(b.situation()),
                    starComponentOf(b.task()),
                    starComponentOf(b.action()),
                    starComponentOf(b.result()));
        }

        private static StarComponentItem starComponentOf(BehavioralEvalOutput.StarComponent c) {
            if (c == null) return null;
            return new StarComponentItem(c.detected(), c.quality(), c.excerpt());
        }

        private static Feedback feedbackOf(BehavioralEvalOutput.Feedback f) {
            if (f == null) return null;
            return new Feedback(
                    f.strengths() == null ? List.of() : f.strengths(),
                    f.improvements() == null ? List.of() : f.improvements(),
                    f.sampleStrongerAnswerStructure());
        }
    }

    record Conceptual(
            Integer overallScore,
            Completeness completeness,
            String oneLineVerdict,
            Scores scores,
            List<ConceptItem> conceptCoverage,
            LevelCalibrationItem levelCalibration,
            List<MisconceptionItem> misconceptions,
            Feedback feedback
    ) implements EvaluationDetail {

        public record Scores(
                ScoreEntry accuracy,
                ScoreEntry depth,
                ScoreEntry practicalApplication,
                ScoreEntry clarity
        ) {}

        public record ConceptItem(
                String conceptName,
                boolean mentioned,
                Boolean correct,
                String candidateStatement,
                String correction
        ) {}

        public record LevelCalibrationItem(
                String expectedLevel,
                String actualDemonstratedLevel,
                String gap
        ) {}

        public record MisconceptionItem(String claim, String correction) {}

        public record Feedback(
                List<String> strengths,
                List<String> improvements,
                List<String> keyPointsToStudy
        ) {}

        static Conceptual from(ConceptualEvalOutput o) {
            return new Conceptual(
                    o.overallScore(),
                    o.completeness(),
                    o.summary() == null ? null : o.summary().oneLineVerdict(),
                    o.scores() == null ? null : new Scores(
                            scoreEntryOf(o.scores().accuracy()),
                            scoreEntryOf(o.scores().depth()),
                            scoreEntryOf(o.scores().practicalApplication()),
                            scoreEntryOf(o.scores().clarity())),
                    o.conceptCoverage() == null ? List.of()
                            : o.conceptCoverage().stream()
                                    .map(c -> new ConceptItem(
                                            c.conceptName(), c.mentioned(), c.correct(),
                                            c.candidateStatement(), c.correction()))
                                    .toList(),
                    levelCalibrationOf(o.levelCalibration()),
                    o.misconceptions() == null ? List.of()
                            : o.misconceptions().stream()
                                    .map(m -> new MisconceptionItem(m.claim(), m.correction()))
                                    .toList(),
                    feedbackOf(o.feedback()));
        }

        private static LevelCalibrationItem levelCalibrationOf(ConceptualEvalOutput.LevelCalibration l) {
            if (l == null) return null;
            return new LevelCalibrationItem(
                    l.expectedLevel(), l.actualDemonstratedLevel(), l.gap());
        }

        private static Feedback feedbackOf(ConceptualEvalOutput.Feedback f) {
            if (f == null) return null;
            return new Feedback(
                    f.strengths() == null ? List.of() : f.strengths(),
                    f.improvements() == null ? List.of() : f.improvements(),
                    f.keyPointsToStudy() == null ? List.of() : f.keyPointsToStudy());
        }
    }

    record LiveCoding(
            Integer overallScore,
            Completeness completeness,
            String oneLineVerdict,
            Scores scores,
            Complexity detectedComplexity,
            Complexity optimalComplexity,
            Boolean isOptimal,
            List<CodeIssueItem> codeIssues,
            Feedback feedback
    ) implements EvaluationDetail {

        public record Scores(
                ScoreEntry timeComplexity,
                ScoreEntry spaceComplexity,
                ScoreEntry codeQuality,
                ScoreEntry problemSolving
        ) {}

        public record Complexity(String time, String space) {}

        public record CodeIssueItem(String type, Integer line, String detail) {}

        public record Feedback(
                List<String> strengths,
                List<String> improvements,
                String optimizationHint,
                String sampleOptimalSolution
        ) {}

        static LiveCoding from(LiveCodingEvalOutput o) {
            return new LiveCoding(
                    o.overallScore(),
                    o.completeness(),
                    o.summary() == null ? null : o.summary().oneLineVerdict(),
                    o.scores() == null ? null : new Scores(
                            scoreEntryOf(o.scores().timeComplexity()),
                            scoreEntryOf(o.scores().spaceComplexity()),
                            scoreEntryOf(o.scores().codeQuality()),
                            scoreEntryOf(o.scores().problemSolving())),
                    complexityOf(o.analysis() == null ? null : o.analysis().detectedComplexity()),
                    complexityOf(o.analysis() == null ? null : o.analysis().optimalComplexity()),
                    o.analysis() == null ? null : o.analysis().isOptimal(),
                    o.analysis() == null || o.analysis().codeIssues() == null ? List.of()
                            : o.analysis().codeIssues().stream()
                                    .map(i -> new CodeIssueItem(i.type(), i.line(), i.detail()))
                                    .toList(),
                    feedbackOf(o.feedback()));
        }

        private static Complexity complexityOf(LiveCodingEvalOutput.ComplexityInfo c) {
            if (c == null) return null;
            return new Complexity(c.time(), c.space());
        }

        private static Feedback feedbackOf(LiveCodingEvalOutput.Feedback f) {
            if (f == null) return null;
            return new Feedback(
                    f.strengths() == null ? List.of() : f.strengths(),
                    f.improvements() == null ? List.of() : f.improvements(),
                    f.optimizationHint(),
                    f.sampleOptimalSolution());
        }
    }

    // ── Shared ───────────────────────────────────────────────────────────────

    /**
     * Wraps the {@code score} + AI {@code note} pair so the FE can render a
     * progress bar with the rationale inline.
     */
    record ScoreEntry(Integer score, String note) {}

    private static ScoreEntry scoreEntryOf(com.mockwise.interview.dto.assessment.input.ScoreItem item) {
        return item == null ? null : new ScoreEntry(item.score(), item.note());
    }
}
