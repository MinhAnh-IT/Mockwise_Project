package com.mockwise.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.dto.assessment.AssessmentVerdict;
import com.mockwise.interview.dto.assessment.EvaluationDetail;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;
import com.mockwise.interview.enums.QuestionType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Single-answer projection for the FE poll endpoint
 * {@code GET /interviews/{sid}/answers/{aid}} and the embedded
 * per-question detail on {@code GET /interviews/{sid}}. Carries enough
 * state for the UI to render "scoring…" / "ready" / "failed" plus the
 * verdict + feedback once SCORED.
 *
 * <p>{@code verdict} is the strongly-typed canonical
 * {@link AssessmentVerdict} the planner reads — distilled from the AI
 * payload to a flat set of categorical decisions.
 *
 * <p>{@code evaluationDetail} is the curated user-facing projection of
 * the same AI payload (per-dimension scores, signal / concept coverage,
 * red flags, code issues) — what the report UI actually renders. Variants
 * are discriminated by {@code kind} (matches the question type one-for-one).
 *
 * <p>{@code mediaUrl} is a same-origin path to the candidate's submitted
 * video, populated only when results are revealed (session SCORED) and
 * the answer carries a storage object id. The path is JWT-protected
 * end-to-end — see storage-service's {@code InterviewVideoController}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerView(
        UUID answerId,
        UUID sessionId,
        UUID sessionQuestionId,
        AnswerType type,
        AnswerStatus status,
        Float score,
        Float maxScore,
        String feedback,
        AssessmentVerdict verdict,
        EvaluationDetail evaluationDetail,
        String errorCode,
        String errorMessage,
        OffsetDateTime submittedAt,
        OffsetDateTime scoredAt,
        UUID storageObjectId,
        String mediaUrl,
        // CODE answers only, revealed once SCORED: the candidate's submitted
        // source + the judge's per-case roster so the report can show the
        // code and which test cases passed. Null for VIDEO answers and
        // while the session is still in progress.
        Coding coding,
        // VIDEO answers only, revealed once SCORED: the transcript that
        // actually scored this answer — the real-time one on the Lever 2 fast
        // path, else the batch one. Surfaced so the report shows the same text
        // the AI graded (realtime-stt-plan.md §8.1 #4), never a divergent one.
        String transcript
) {

    /**
     * Submitted code + judge outcome for a LIVE_CODING answer. Sourced from
     * {@code answer.code/language} and the judge summary persisted on
     * {@code rubric_scores.judge} (runnable path) or {@code raw_evaluation}
     * (non-runnable CE/RE path).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Coding(
            String code,
            String language,
            String judgeVerdict,
            Integer testsPassed,
            Integer testsTotal,
            List<CaseResult> cases
    ) {
        public record CaseResult(String testCaseId, String status) {}
    }

    /**
     * Masks score / feedback / verdict / evaluation detail when
     * {@code revealResults} is false — used while the session is still in
     * progress so the candidate cannot peek at per-question grading. Status,
     * errorCode, errorMessage and the timestamps stay exposed so the FE can
     * still render "submitted → scoring → done" transitions between questions.
     * Once the session reaches SCORED the caller passes {@code revealResults
     * = true}.
     *
     * <p>{@code questionType} is needed to deserialise {@code raw_evaluation}
     * into the right {@link EvaluationDetail} variant. Callers that don't
     * have the type handy (e.g. transient polls before the planner has
     * pinned a follow-up) may pass null — the detail is dropped.
     *
     * <p>{@code mediaUrl} stays null here — the caller invokes
     * {@link #withSignedMedia} after the fact so {@code AnswerView} stays
     * decoupled from the storage adapter. Verdict deserialisation is best
     * effort: a malformed JSONB blob falls back to null rather than failing
     * the whole response.
     */
    public static AnswerView fromEntity(
            Answer a,
            QuestionType questionType,
            boolean revealResults,
            ObjectMapper objectMapper) {
        AssessmentVerdict typedVerdict = revealResults ? toVerdict(a.getVerdict(), objectMapper) : null;
        EvaluationDetail detail = revealResults
                ? EvaluationDetail.fromRaw(questionType, a.getRawEvaluation(), objectMapper)
                : null;
        Coding coding = (revealResults && a.getType() == AnswerType.CODE)
                ? codingOf(a)
                : null;
        String transcript = (revealResults && a.getType() == AnswerType.VIDEO)
                ? scoredTranscript(a)
                : null;
        return new AnswerView(
                a.getId(),
                a.getSessionId(),
                a.getSessionQuestionId(),
                a.getType(),
                a.getStatus(),
                revealResults ? a.getScore() : null,
                revealResults ? a.getMaxScore() : null,
                revealResults ? a.getFeedback() : null,
                typedVerdict,
                detail,
                a.getErrorCode(),
                a.getErrorMessage(),
                a.getSubmittedAt(),
                a.getScoredAt(),
                revealResults ? a.getStorageObjectId() : null,
                /* mediaUrl */ null,
                coding,
                transcript);
    }

    /** The transcript that scored a VIDEO answer: real-time (fast path) first, else batch. */
    private static String scoredTranscript(Answer a) {
        if (a.getRealtimeTranscript() != null && !a.getRealtimeTranscript().isBlank()) {
            return a.getRealtimeTranscript();
        }
        if (a.getAuthoritativeTranscript() != null && !a.getAuthoritativeTranscript().isBlank()) {
            return a.getAuthoritativeTranscript();
        }
        return null;
    }

    /**
     * Returns a copy with {@code mediaUrl} filled in. {@code signer} takes
     * a {@code storageObjectId} and yields a URL the FE can drop into a
     * {@code <video src>}. Caller is expected to only invoke this when
     * results are revealed; we still soft-fail if the answer has no storage
     * object (CODE answers, or VIDEO answers that never reached READY).
     */
    public AnswerView withSignedMedia(Function<UUID, String> signer) {
        if (storageObjectId == null || signer == null) {
            return this;
        }
        String url = signer.apply(storageObjectId);
        if (url == null || url.isBlank()) {
            return this;
        }
        return new AnswerView(
                answerId, sessionId, sessionQuestionId, type, status,
                score, maxScore, feedback, verdict, evaluationDetail,
                errorCode, errorMessage, submittedAt, scoredAt,
                storageObjectId, url, coding, transcript);
    }

    /**
     * Builds the coding projection from the persisted judge summary. The
     * runnable path stores it on {@code rubric_scores.judge}; the
     * non-runnable (CE/RE) path stores the same numbers on
     * {@code raw_evaluation}. Best-effort — a missing/legacy shape yields a
     * Coding with just the code/language filled in.
     */
    @SuppressWarnings("unchecked")
    private static Coding codingOf(Answer a) {
        Map<String, Object> judge = null;
        Map<String, Object> rubric = a.getRubricScores();
        if (rubric != null && rubric.get("judge") instanceof Map<?, ?> j) {
            judge = (Map<String, Object>) j;
        }
        Map<String, Object> raw = a.getRawEvaluation();
        Map<String, Object> src = judge != null
                ? judge
                : (raw != null && (raw.containsKey("testsTotal") || raw.containsKey("judgeVerdict"))
                        ? raw : null);

        String judgeVerdict = src == null ? null
                : asStr(src.containsKey("verdict") ? src.get("verdict") : src.get("judgeVerdict"));
        Integer testsPassed = src == null ? null : asInt(src.get("testsPassed"));
        Integer testsTotal = src == null ? null : asInt(src.get("testsTotal"));

        List<Coding.CaseResult> cases = new ArrayList<>();
        if (src != null && src.get("cases") instanceof List<?> cs) {
            for (Object o : cs) {
                if (o instanceof Map<?, ?> cm) {
                    Map<String, Object> m = (Map<String, Object>) cm;
                    cases.add(new Coding.CaseResult(asStr(m.get("testCaseId")), asStr(m.get("status"))));
                }
            }
        }
        return new Coding(a.getCode(), a.getLanguage(), judgeVerdict, testsPassed, testsTotal, cases);
    }

    private static String asStr(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return null;
        try {
            return Integer.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static AssessmentVerdict toVerdict(Map<String, Object> raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isEmpty() || objectMapper == null) return null;
        try {
            return objectMapper.convertValue(raw, AssessmentVerdict.class);
        } catch (IllegalArgumentException ex) {
            // JSONB column may carry a legacy shape on rows scored before the
            // verdict mapper stabilised. Falling back to null keeps the rest
            // of the response usable instead of failing the whole call.
            return null;
        }
    }
}
