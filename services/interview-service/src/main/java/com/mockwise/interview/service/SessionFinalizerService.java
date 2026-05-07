package com.mockwise.interview.service;

import com.mockwise.interview.client.userprofile.UserProfileAdapter;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.message.constants.KafkaTopics;
import com.mockwise.interview.repository.AnswerRepository;
import com.mockwise.interview.repository.InterviewBlueprintRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import com.mockwise.interview.repository.SessionQuestionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the "session is COMPLETED + every answer is terminal — kick off the
 * AI overall reviewer" gate. Three call sites compete to fire the event:
 * the per-answer evaluation-completed consumer, the failure consumers, and
 * the user-triggered /finish endpoint. Idempotency is enforced via a
 * {@code metadata.sessionEvalRequestedAt} flag plus a row-level lock on
 * the session row, so only one outbox event per session is staged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionFinalizerService {

    static final String META_REQUESTED_AT = "sessionEvalRequestedAt";

    InterviewSessionRepository sessionRepo;
    InterviewBlueprintRepository blueprintRepo;
    SessionQuestionRepository sessionQuestionRepo;
    AnswerRepository answerRepo;
    OutboxWriter outboxWriter;
    UserProfileAdapter userProfileAdapter;

    /**
     * Evaluates the gate and stages a {@code session-evaluation-requested}
     * event when it fires. No-op when the session is not COMPLETED, when
     * an event has already been staged, or when at least one answer is
     * still mid-flight.
     */
    @Transactional
    public void maybeRequestOverallReview(UUID sessionId) {
        InterviewSession s = sessionRepo.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));

        if (s.getStatus() != SessionStatus.COMPLETED) {
            return;
        }
        Map<String, Object> meta = s.getMetadata() != null ? s.getMetadata() : new HashMap<>();
        if (meta.get(META_REQUESTED_AT) != null) {
            return;
        }
        if (!allAnswersTerminal(sessionId)) {
            return;
        }

        Map<String, Object> payload = buildPayload(s);
        outboxWriter.stage(KafkaTopics.SESSION_EVALUATION_REQUESTED,
                "SESSION_EVALUATION_REQUESTED",
                s.getId(),
                payload);

        meta.put(META_REQUESTED_AT, OffsetDateTime.now().toString());
        s.setMetadata(meta);
        sessionRepo.save(s);

        log.info("Staged session-evaluation-requested for session {} ({} answers terminal)",
                s.getId(), payload.get("answerCount"));
    }

    private boolean allAnswersTerminal(UUID sessionId) {
        long pinned = sessionQuestionRepo.countBySessionId(sessionId);
        long terminal = answerRepo.countBySessionIdAndStatusIn(sessionId,
                List.of(AnswerStatus.SCORED, AnswerStatus.FAILED));
        return pinned > 0 && pinned == terminal;
    }

    /**
     * Assembles the cross-question payload the AI overall_reviewer graph
     * consumes. Field names are camelCase so the Python side's
     * {@code CamelModel} deserialises directly into
     * {@code SessionEvaluationPayload}.
     */
    private Map<String, Object> buildPayload(InterviewSession s) {
        InterviewBlueprint bp = s.getBlueprintId() != null
                ? blueprintRepo.findById(s.getBlueprintId()).orElse(null)
                : null;

        List<SessionQuestion> questions = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(s.getId());
        // Index answers by sessionQuestionId for O(1) lookup while walking pinned questions.
        Map<UUID, Answer> answerByQ = new HashMap<>();
        for (Answer a : answerRepo.findBySessionId(s.getId())) {
            answerByQ.put(a.getSessionQuestionId(), a);
        }

        List<Map<String, Object>> answers = new java.util.ArrayList<>(questions.size());
        for (SessionQuestion sq : questions) {
            Answer a = answerByQ.get(sq.getId());
            Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sessionQuestionId", sq.getId().toString());
            entry.put("sequence", sq.getSequence());
            entry.put("questionType", sq.getQuestionType().name());
            entry.put("topicKind", sq.getTopicKind() != null ? sq.getTopicKind().name() : null);
            entry.put("topicValue", sq.getTopicValue());
            entry.put("difficulty", sq.getDifficulty() != null ? sq.getDifficulty().name() : null);
            entry.put("isFollowUp", sq.isFollowUp());
            entry.put("text", sq.getInlineText() != null ? sq.getInlineText() : snap.get("text"));
            entry.put("expectedSignals", snap.get("expectedSignals"));
            entry.put("keyConcepts", snap.get("keyConcepts"));
            entry.put("depthExpected", snap.get("depthExpected"));

            if (a != null) {
                entry.put("answerStatus", a.getStatus().name());
                entry.put("perAnswerScore", a.getScore());
                entry.put("perAnswerVerdict", a.getVerdict());
                if (a.getType() == AnswerType.CODE) {
                    entry.put("code", a.getCode());
                    entry.put("language", a.getLanguage());
                } else {
                    entry.put("transcript", extractTranscript(a));
                }
            } else {
                // Pinned but never answered — should not happen when the gate
                // fires, but include a placeholder so the AI's per-topic
                // summary can mark the topic NOT_TESTED.
                entry.put("answerStatus", "MISSING");
            }
            answers.add(entry);
        }

        Map<String, Object> blueprintSummary = new LinkedHashMap<>();
        if (bp != null) {
            blueprintSummary.put("id", bp.getId().toString());
            blueprintSummary.put("questionBudget", bp.getQuestionBudget());
            blueprintSummary.put("timeBudgetMinutes", bp.getTimeBudgetMinutes());
            blueprintSummary.put("topics", bp.getTopics());
        }

        // Pull the candidate's preferred output language so the overall
        // reviewer narrative (summary / strengths / weaknesses / per-topic
        // comments / recommendations) lands in Vietnamese for VN candidates.
        // Profile fetch is soft-fail — a transient user-profile-service
        // outage shouldn't block the session report.
        String responseLanguage = "vi";
        try {
            UserProfileResponse profile = userProfileAdapter.getProfile(s.getUserId());
            if (profile != null && profile.preferredLanguage() != null) {
                String pref = profile.preferredLanguage().toLowerCase();
                if ("vi".equals(pref) || "en".equals(pref)) {
                    responseLanguage = pref;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to load profile for overall-review language for session {}: {}",
                    s.getId(), ex.getMessage());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", s.getId().toString());
        payload.put("userId", s.getUserId());
        payload.put("targetRole", s.getTargetRole());
        payload.put("level", s.getLevel());
        payload.put("interviewType", s.getInterviewType().name());
        payload.put("responseLanguage", responseLanguage);
        payload.put("blueprint", blueprintSummary);
        payload.put("answers", answers);
        payload.put("answerCount", answers.size());
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "SESSION_EVALUATION_REQUESTED");
        payload.put("occurredAt", OffsetDateTime.now().toString());
        return payload;
    }

    private static String extractTranscript(Answer a) {
        if (a == null || a.getRawEvaluation() == null) return "";
        Object answer = a.getRawEvaluation().get("answer");
        if (answer instanceof Map<?, ?> m) {
            Object t = m.get("transcript");
            if (t instanceof String s) return s;
        }
        return "";
    }
}
