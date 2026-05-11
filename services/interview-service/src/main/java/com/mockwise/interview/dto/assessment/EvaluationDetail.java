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
 * subset of fields that have a sensible presentation: per-dimension scores,
 * named-signal / concept coverage, red flags, code issues, misconceptions.
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
            Scores scores,
            List<SignalItem> signalCoverage,
            List<RedFlagItem> redFlags
    ) implements EvaluationDetail {

        public record Scores(
                Integer starStructure,
                Integer relevance,
                Integer specificity,
                Integer impactResult,
                Integer selfAwareness
        ) {}

        public record SignalItem(String signalName, boolean detected) {}

        public record RedFlagItem(String type, String severity) {}

        static Behavioral from(BehavioralEvalOutput o) {
            return new Behavioral(
                    o.overallScore(),
                    o.completeness(),
                    o.scores() == null ? null : new Scores(
                            scoreOf(o.scores().starStructure()),
                            scoreOf(o.scores().relevance()),
                            scoreOf(o.scores().specificity()),
                            scoreOf(o.scores().impactResult()),
                            scoreOf(o.scores().selfAwareness())),
                    o.signalCoverage() == null ? List.of()
                            : o.signalCoverage().stream()
                                    .map(s -> new SignalItem(s.signalName(), s.detected()))
                                    .toList(),
                    o.redFlags() == null ? List.of()
                            : o.redFlags().stream()
                                    .map(f -> new RedFlagItem(f.type(), f.severity()))
                                    .toList());
        }
    }

    record Conceptual(
            Integer overallScore,
            Completeness completeness,
            Scores scores,
            List<ConceptItem> conceptCoverage,
            List<MisconceptionItem> misconceptions
    ) implements EvaluationDetail {

        public record Scores(
                Integer accuracy,
                Integer depth,
                Integer practicalApplication,
                Integer clarity
        ) {}

        public record ConceptItem(
                String conceptName,
                boolean mentioned,
                Boolean correct
        ) {}

        public record MisconceptionItem(String claim) {}

        static Conceptual from(ConceptualEvalOutput o) {
            return new Conceptual(
                    o.overallScore(),
                    o.completeness(),
                    o.scores() == null ? null : new Scores(
                            scoreOf(o.scores().accuracy()),
                            scoreOf(o.scores().depth()),
                            scoreOf(o.scores().practicalApplication()),
                            scoreOf(o.scores().clarity())),
                    o.conceptCoverage() == null ? List.of()
                            : o.conceptCoverage().stream()
                                    .map(c -> new ConceptItem(c.conceptName(), c.mentioned(), c.correct()))
                                    .toList(),
                    o.misconceptions() == null ? List.of()
                            : o.misconceptions().stream()
                                    .map(m -> new MisconceptionItem(m.claim()))
                                    .toList());
        }
    }

    record LiveCoding(
            Integer overallScore,
            Completeness completeness,
            Scores scores,
            Boolean isOptimal,
            List<CodeIssueItem> codeIssues
    ) implements EvaluationDetail {

        public record Scores(
                Integer timeComplexity,
                Integer spaceComplexity,
                Integer codeQuality,
                Integer problemSolving
        ) {}

        public record CodeIssueItem(String type, String detail) {}

        static LiveCoding from(LiveCodingEvalOutput o) {
            return new LiveCoding(
                    o.overallScore(),
                    o.completeness(),
                    o.scores() == null ? null : new Scores(
                            scoreOf(o.scores().timeComplexity()),
                            scoreOf(o.scores().spaceComplexity()),
                            scoreOf(o.scores().codeQuality()),
                            scoreOf(o.scores().problemSolving())),
                    o.analysis() == null ? null : o.analysis().isOptimal(),
                    o.analysis() == null || o.analysis().codeIssues() == null ? List.of()
                            : o.analysis().codeIssues().stream()
                                    .map(i -> new CodeIssueItem(i.type(), i.detail()))
                                    .toList());
        }
    }

    private static Integer scoreOf(com.mockwise.interview.dto.assessment.input.ScoreItem item) {
        return item == null ? null : item.score();
    }
}
