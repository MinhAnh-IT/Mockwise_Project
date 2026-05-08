package com.mockwise.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.QuestionSource;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.TopicKind;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * What the FE needs to render one pinned question. Carries enough metadata
 * for the player UI plus follow-up linkage so the FE can show "Câu này
 * tiếp nối câu trước".
 *
 * <p>{@code @JsonInclude(NON_NULL)} keeps the wire shrunk: when
 * {@link #redacted} nulls every rubric/classification field, those keys
 * vanish from the JSON entirely instead of leaking as {@code "field": null}.
 * {@code isFollowUp} is the wrapper {@link Boolean} (not primitive) so it
 * can be nulled and dropped the same way.
 *
 * <p>Two ways to render audio:
 * <ul>
 *   <li>{@code audioUrl} (preferred) — short-lived presigned GET URL the
 *       FE can stream directly. Built by {@link #withSignedAudio} when
 *       the caller has a {@code StorageAdapter} on hand.</li>
 *   <li>{@code audioKey} — the raw object key. Falls back to this when
 *       the URL signer is unavailable (storage down, key missing).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PinnedQuestionView(
        UUID sessionQuestionId,
        int sequence,
        String questionId,
        QuestionType questionType,
        TopicKind topicKind,
        String topicValue,
        Difficulty difficulty,
        QuestionSource source,
        Boolean isFollowUp,
        UUID parentSessionQuestionId,
        String text,
        String audioKey,
        String audioUrl,
        List<String> expectedPoints,
        // Latest answer pinned to this session_question — populated only on
        // the SCORED report response so the FE can lazily fetch the per-
        // question verdict + media. While the session is in flight the
        // candidate's view of their own previous answers stays hidden, so
        // {@link #redacted} drops this field too.
        UUID latestAnswerId
) {

    /** Build from an entity. {@code audioUrl} stays null — call {@link #withSignedAudio}. */
    public static PinnedQuestionView fromEntity(SessionQuestion sq) {
        Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();
        String text = sq.getInlineText() != null
                ? sq.getInlineText()
                : (String) snap.get("text");
        String audioKey = (String) snap.get("audioKey");
        List<String> expectedPoints = sq.getInlineExpectedPoints() != null
                ? sq.getInlineExpectedPoints()
                : extractStringList(snap.get("expectedPoints"));

        return new PinnedQuestionView(
                sq.getId(),
                sq.getSequence(),
                sq.getQuestionId(),
                sq.getQuestionType(),
                sq.getTopicKind(),
                sq.getTopicValue(),
                sq.getDifficulty(),
                sq.getSource(),
                sq.isFollowUp(),
                sq.getParentSessionQuestionId(),
                text,
                audioKey,
                /* audioUrl */ null,
                expectedPoints,
                /* latestAnswerId */ null
        );
    }

    /**
     * Returns a copy with the audio URL filled in. {@code signer} is a
     * function that takes an objectKey and yields a presigned URL — the
     * service layer wires this to {@code StorageAdapter::signQuestionAudioUrl}.
     * Caller passes a no-op when audio signing isn't desired (e.g. tests).
     */
    public PinnedQuestionView withSignedAudio(Function<String, String> signer) {
        if (audioKey == null || audioKey.isBlank() || signer == null) {
            return this;
        }
        String url = signer.apply(audioKey);
        if (url == null || url.isBlank()) {
            return this;
        }
        return new PinnedQuestionView(
                sessionQuestionId, sequence, questionId, questionType,
                topicKind, topicValue, difficulty, source, isFollowUp,
                parentSessionQuestionId, text, audioKey, url, expectedPoints,
                latestAnswerId);
    }

    /**
     * Returns a copy with {@code latestAnswerId} filled in. Called from the
     * SCORED report path so the FE can lazily fetch the per-question verdict
     * + media; mid-flight callers leave this null and let {@link #redacted}
     * keep it hidden.
     */
    public PinnedQuestionView withLatestAnswerId(UUID answerId) {
        if (answerId == null) return this;
        return new PinnedQuestionView(
                sessionQuestionId, sequence, questionId, questionType,
                topicKind, topicValue, difficulty, source, isFollowUp,
                parentSessionQuestionId, text, audioKey, audioUrl, expectedPoints,
                answerId);
    }

    /**
     * Strips every field that isn't strictly needed for the candidate to
     * read and answer the question: {@code expectedPoints} (the answer key),
     * {@code difficulty} (biases the candidate), {@code topicKind} /
     * {@code topicValue} / {@code source} (internal pipeline metadata),
     * {@code questionId} (internal DB id), {@code audioKey} (raw object key
     * — {@code audioUrl} is already presigned), {@code parentSessionQuestionId}
     * and {@code isFollowUp} (planner classification — the audio itself
     * conveys whether a question follows up on the previous one).
     *
     * <p>Keeps the five fields the FE actually needs to render and submit
     * answers: {@code sessionQuestionId}, {@code sequence},
     * {@code questionType}, {@code text}, {@code audioUrl}.
     */
    public PinnedQuestionView redacted() {
        return new PinnedQuestionView(
                sessionQuestionId,
                sequence,
                /* questionId */ null,
                questionType,
                /* topicKind */ null,
                /* topicValue */ null,
                /* difficulty */ null,
                /* source */ null,
                /* isFollowUp */ null,
                /* parentSessionQuestionId */ null,
                text,
                /* audioKey */ null,
                audioUrl,
                /* expectedPoints */ null,
                /* latestAnswerId */ null);
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Object o) {
        return o instanceof List<?> l ? (List<String>) l : null;
    }
}
