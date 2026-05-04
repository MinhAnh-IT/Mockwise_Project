package com.mockwise.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.dto.assessment.AssessmentVerdict;
import com.mockwise.interview.mapper.AssessmentVerdictMapper;
import com.mockwise.interview.dto.assessment.input.BehavioralEvalOutput;
import com.mockwise.interview.dto.assessment.input.ConceptualEvalOutput;
import com.mockwise.interview.dto.assessment.input.LiveCodingEvalOutput;
import com.mockwise.interview.client.storage.StorageAdapter;
import com.mockwise.interview.client.storage.dto.StorageObjectResponse;
import com.mockwise.interview.client.userprofile.UserProfileAdapter;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.entity.AnswerEventLog;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.entity.SessionTopicStateId;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.enums.TopicKind;
import com.mockwise.interview.enums.TopicStatus;
import com.mockwise.interview.message.constants.KafkaTopics;
import com.mockwise.interview.planner.NextQuestionPlanner;
import com.mockwise.interview.planner.PlannerDecision;
import com.mockwise.interview.planner.PlannerInputs;
import com.mockwise.interview.planner.PlannerOutcome;
import com.mockwise.interview.repository.AnswerEventLogRepository;
import com.mockwise.interview.repository.AnswerRepository;
import com.mockwise.interview.repository.InterviewBlueprintRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import com.mockwise.interview.repository.SessionQuestionRepository;
import com.mockwise.interview.repository.SessionTopicStateRepository;
import com.mockwise.interview.dto.request.SubmitAnswerInput;
import com.mockwise.interview.dto.response.SubmitAnswerOutput;
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
 * Owns the answer side of the orchestrator: ingest a submission from the
 * FE, gate it on storage / state-machine rules, persist a row in
 * SUBMITTED → PROCESSING, and stage the {@code answer-submitted} or
 * {@code code-submission} event for the outbox poller to publish.
 *
 * <p>The post-evaluation entry point ({@link #applyEvaluationCompleted})
 * is what makes the orchestrator "smart": consume an
 * {@code evaluation-completed} payload, derive the canonical verdict,
 * call the planner, apply its side effects, and either pin the next
 * question or finalise the session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class
AnswerService {

    AnswerRepository answerRepo;
    AnswerEventLogRepository answerEventLogRepo;
    SessionQuestionRepository sessionQuestionRepo;
    SessionTopicStateRepository topicStateRepo;
    InterviewSessionRepository sessionRepo;
    InterviewBlueprintRepository blueprintRepo;

    StorageAdapter storageAdapter;
    UserProfileAdapter userProfileAdapter;
    OutboxWriter outboxWriter;

    AssessmentVerdictMapper verdictMapper;
    NextQuestionPlanner planner;
    SideEffectApplier sideEffectApplier;
    QuestionPicker questionPicker;
    ObjectMapper objectMapper;

    // ── Submit (FE → orchestrator) ───────────────────────────────────────────

    /**
     * Persists a fresh answer row for the given session_question. For
     * VIDEO answers the storage object is verified end-to-end against
     * storage-service before commit (ownership / kind / READY status).
     *
     * <p>Flips the row through SUBMITTED → PROCESSING in the same
     * transaction, then stages the appropriate outbox event so the
     * worker pipeline can pick up.
     */
    @Transactional
    public SubmitAnswerOutput submit(
            UUID sessionId, UUID sessionQuestionId, SubmitAnswerInput input, String userId) {

        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(StatusCode.SESSION_NOT_OWNER);
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(StatusCode.SESSION_NOT_IN_PROGRESS);
        }

        SessionQuestion sq = sessionQuestionRepo.findById(sessionQuestionId)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));
        if (!sq.getSessionId().equals(sessionId)) {
            throw new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION);
        }

        // Pre-flight checks specific to type.
        if (input.type() == AnswerType.VIDEO) {
            verifyStorageObject(input.storageObjectId(), userId);
        } else {
            if (input.code() == null || input.code().isBlank()) {
                throw new BusinessException(StatusCode.PLACEHOLDER, "code is required for CODE answers");
            }
        }

        Answer answer = answerRepo.save(Answer.builder()
                .sessionId(sessionId)
                .sessionQuestionId(sessionQuestionId)
                .type(input.type())
                .status(AnswerStatus.PROCESSING)  // we'll publish immediately, no value to a SUBMITTED tick
                .storageObjectId(input.storageObjectId())
                .code(input.code())
                .language(input.language())
                .submittedAt(OffsetDateTime.now())
                .build());

        logTransition(answer.getId(), null, AnswerStatus.PROCESSING,
                input.type() == AnswerType.VIDEO ? "video-answer-submitted" : "code-answer-submitted",
                Map.of());

        stageOutbound(answer, sq);

        return new SubmitAnswerOutput(answer.getId(), answer.getStatus(), answer.getSubmittedAt());
    }

    private void verifyStorageObject(UUID storageObjectId, String userId) {
        if (storageObjectId == null) {
            throw new BusinessException(StatusCode.PLACEHOLDER, "storageObjectId is required for VIDEO answers");
        }

        StorageObjectResponse object;
        try {
            object = storageAdapter.getObject(storageObjectId);
        } catch (BusinessException e) {
            // 404 from storage = treat as unknown object; everything else
            // bubbles up so the caller sees the upstream code.
            if (e.getHttpStatus() == 404) {
                throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_READY);
            }
            throw e;
        }
        if (!"INTERVIEW_VIDEO".equals(object.kind())) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_WRONG_KIND);
        }
        if (!"READY".equals(object.status())) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_READY);
        }
        if (!userId.equals(object.ownerUserId())) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_OWNER_MISMATCH);
        }
        // Storage object reuse is checked via answer.storage_object_id index +
        // a follow-up uniqueness query — added when we build the answers index in
        // V2. For now the FE flow makes a fresh upload per submission.
    }

    private void stageOutbound(Answer answer, SessionQuestion sq) {
        if (answer.getType() == AnswerType.VIDEO) {
            // tts-stt picks this up.
            outboxWriter.stage(
                    KafkaTopics.ANSWER_SUBMITTED,
                    "ANSWER_SUBMITTED",
                    answer.getId(),
                    Map.of(
                            "answerId", answer.getId().toString(),
                            "sessionId", answer.getSessionId().toString(),
                            "sessionQuestionId", sq.getId().toString(),
                            "questionId", String.valueOf(sq.getQuestionId()),
                            "storageObjectId", answer.getStorageObjectId().toString()
                    ));
        } else {
            // judge-service picks this up.
            outboxWriter.stage(
                    KafkaTopics.CODE_SUBMISSION,
                    "CODE_SUBMISSION",
                    answer.getId(),
                    Map.of(
                            "submissionId", answer.getId().toString(),
                            "sessionId", answer.getSessionId().toString(),
                            "questionId", String.valueOf(sq.getQuestionId()),
                            "code", answer.getCode(),
                            "language", String.valueOf(answer.getLanguage())
                    ));
        }
    }

    // ── Apply evaluation-completed (Kafka consumer entry point) ──────────────

    /**
     * Called by the {@code evaluation-completed} consumer (Phase G). The
     * raw payload is the {@code result} field from
     * {@code EvaluationCompletedEvent}. We dispatch on the question type
     * stored on session_question (more reliable than the AI's
     * {@code interview_type} string), derive the verdict, persist it,
     * and run the planner + side-effect applier.
     *
     * <p>Idempotency: an answer already in SCORED is a no-op return —
     * the consumer's processed_event row guards against double delivery,
     * but we belt-and-brace here so a manual replay through
     * {@code /internal/replay/{aid}} stays safe.
     */
    @Transactional
    public void applyEvaluationCompleted(UUID answerId, Map<String, Object> rawEvaluation) {
        Answer answer = answerRepo.findById(answerId)
                .orElseThrow(() -> new BusinessException(StatusCode.ANSWER_NOT_FOUND));
        if (answer.getStatus() == AnswerStatus.SCORED) {
            log.debug("Answer {} already SCORED — ignoring duplicate evaluation-completed", answerId);
            return;
        }

        SessionQuestion sq = sessionQuestionRepo.findById(answer.getSessionQuestionId())
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));
        InterviewSession session = sessionRepo.findById(answer.getSessionId())
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));

        AssessmentVerdict verdict = deriveVerdict(sq.getQuestionType(), rawEvaluation);

        // Persist verdict + flip to SCORED.
        AnswerStatus prev = answer.getStatus();
        answer.setStatus(AnswerStatus.SCORED);
        answer.setScoredAt(OffsetDateTime.now());
        answer.setScore(verdict.scoreNormalized());
        answer.setMaxScore(10.0f);
        answer.setVerdict(verdictToMap(verdict));
        answer.setRawEvaluation(rawEvaluation);
        answerRepo.save(answer);
        logTransition(answerId, prev, AnswerStatus.SCORED, "evaluation-completed", Map.of());

        // Run the planner — this is the "smart" tick.
        if (sq.getTopicKind() == null || sq.getTopicValue() == null) {
            // Coding answers may not be tied to a topic in the matrix yet;
            // skip the planner for them until we extend the blueprint shape.
            log.info("Answer {} has no topic — skipping planner", answerId);
            return;
        }
        runPlannerForAnswer(session, sq, verdict);
    }

    private void runPlannerForAnswer(InterviewSession session, SessionQuestion sq, AssessmentVerdict verdict) {
        InterviewBlueprint blueprint = blueprintRepo.findById(session.getBlueprintId())
                .orElseThrow(() -> new BusinessException(StatusCode.BLUEPRINT_NOT_FOUND));

        BlueprintTopic currentTopicConfig = findTopicConfig(blueprint, sq.getTopicKind(), sq.getTopicValue());
        SessionTopicState currentState = topicStateRepo.findById(
                new SessionTopicStateId(session.getId(), sq.getTopicKind(), sq.getTopicValue()))
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));

        // Update last_difficulty / last_score / last_assessment on the topic
        // state up-front so the planner sees the correct context.
        currentState.setLastDifficulty(sq.getDifficulty());
        currentState.setLastScore(verdict.scoreNormalized());
        currentState.setLastAssessment(verdictToMap(verdict));
        topicStateRepo.save(currentState);

        Map<String, TopicStatus> statuses = topicStateRepo.findByIdSessionId(session.getId()).stream()
                .collect(LinkedHashMap::new,
                        (m, st) -> m.put(
                                PlannerInputs.topicKey(toBlueprintTopicForKey(st)),
                                st.getStatus()),
                        Map::putAll);

        long pinnedSoFar = sessionQuestionRepo.countBySessionId(session.getId());

        PlannerInputs inputs = new PlannerInputs(
                session, blueprint, currentState, currentTopicConfig, verdict,
                statuses, (int) pinnedSoFar);
        PlannerOutcome outcome = planner.plan(inputs);

        sideEffectApplier.apply(outcome.sideEffects(), session, currentState);

        // Resolve the decision — might pin a new question or end the session.
        switch (outcome.decision()) {
            case PlannerDecision.AskFollowUp f -> handleFollowUp(session, sq, f, verdict, (int) pinnedSoFar + 1);
            case PlannerDecision.MoveToNextTopic m -> handleMoveNext(session, m, (int) pinnedSoFar + 1);
            case PlannerDecision.EndSession e -> handleEndSession(session, e.reason());
        }
    }

    private void handleFollowUp(
            InterviewSession session, SessionQuestion parent,
            PlannerDecision.AskFollowUp follow, AssessmentVerdict verdict, int sequence) {

        // Tier 1: pre-authored.
        var fromBank = questionPicker.pickFollowUpFromBank(
                session.getId(), parent, follow.weakTarget(), follow.difficulty(), sequence);
        if (fromBank.isPresent()) {
            log.info("Pinned PRE_AUTHORED follow-up for session {} parent {}",
                    session.getId(), parent.getId());
            return;
        }

        // Tier 2: AI-generated. Need parent context — pull from snapshot.
        UserProfileResponse profile = userProfileAdapter.getProfile(session.getUserId());
        String language = profile.preferredLanguage() != null
                ? profile.preferredLanguage().toLowerCase()
                : "vi";

        Map<String, Object> snap = parent.getSnapshot() != null ? parent.getSnapshot() : Map.of();
        String parentText = parent.getInlineText() != null
                ? parent.getInlineText()
                : (String) snap.get("text");
        Answer parentAnswer = answerRepo.findBySessionQuestionId(parent.getId()).orElse(null);
        String parentTranscript = extractTranscript(parentAnswer);

        questionPicker.pickFollowUpFromAi(
                session.getId(), parent, follow.weakTarget(), verdict.strongTargets(),
                follow.difficulty(),
                parentText, parentTranscript,
                (String) snap.get("competency"), (String) snap.get("domain"),
                extractStringList(snap.get("expectedSignals")),
                extractStringList(snap.get("keyConcepts")),
                language, sequence);
        log.info("Pinned AI_GENERATED follow-up for session {} parent {}", session.getId(), parent.getId());
    }

    private void handleMoveNext(InterviewSession session, PlannerDecision.MoveToNextTopic m, int sequence) {
        UserProfileResponse profile = userProfileAdapter.getProfile(session.getUserId());
        List<String> excludeIds = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(session.getId()).stream()
                .map(SessionQuestion::getQuestionId)
                .filter(java.util.Objects::nonNull)
                .toList();

        SessionQuestion next = questionPicker.pickForTopic(
                session.getId(), m.nextTopic(), m.openingDifficulty(), profile, excludeIds, sequence);

        // Promote the new topic to PROBING.
        SessionTopicState ts = topicStateRepo.findById(new SessionTopicStateId(
                session.getId(), m.nextTopic().getKind(), m.nextTopic().getTopicValue()))
                .orElseThrow();
        ts.setStatus(TopicStatus.PROBING);
        ts.setQuestionsAsked(ts.getQuestionsAsked() + 1);
        ts.setLastDifficulty(m.openingDifficulty());
        topicStateRepo.save(ts);

        log.info("Pinned next topic question {} for session {} (topic {}, difficulty {})",
                next.getId(), session.getId(), m.nextTopic().getTopicValue(), m.openingDifficulty());
    }

    private void handleEndSession(InterviewSession session, PlannerDecision.EndSession.EndReason reason) {
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            session.setStatus(SessionStatus.COMPLETED);
            session.setFinishedAt(OffsetDateTime.now());
            sessionRepo.save(session);
        }
        log.info("Session {} ended by planner — reason {}", session.getId(), reason);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AssessmentVerdict deriveVerdict(QuestionType type, Map<String, Object> raw) {
        return switch (type) {
            case BEHAVIORAL ->
                verdictMapper.fromBehavioral(objectMapper.convertValue(raw, BehavioralEvalOutput.class));
            case CORE_CONCEPTUAL ->
                verdictMapper.fromConceptual(objectMapper.convertValue(raw, ConceptualEvalOutput.class));
            case LIVE_CODING ->
                verdictMapper.fromLiveCoding(objectMapper.convertValue(raw, LiveCodingEvalOutput.class));
        };
    }

    private Map<String, Object> verdictToMap(AssessmentVerdict v) {
        // Round-trip through Jackson rather than hand-mapping — keeps the
        // shape consistent with how the FE will deserialise it.
        return objectMapper.convertValue(v, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private void logTransition(UUID answerId, AnswerStatus from, AnswerStatus to,
                               String reason, Map<String, Object> meta) {
        answerEventLogRepo.save(AnswerEventLog.builder()
                .answerId(answerId)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .metadata(new HashMap<>(meta))
                .build());
    }

    private static BlueprintTopic findTopicConfig(InterviewBlueprint bp, TopicKind kind, String value) {
        return bp.getTopics().stream()
                .filter(t -> t.getKind() == kind && value.equals(t.getTopicValue()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Topic " + kind + ":" + value + " missing from blueprint " + bp.getId()));
    }

    /** Helper that adapts a SessionTopicState back to a BlueprintTopic shape just enough for topicKey(). */
    private static BlueprintTopic toBlueprintTopicForKey(SessionTopicState s) {
        return BlueprintTopic.builder()
                .kind(s.getId().getTopicKind())
                .topicValue(s.getId().getTopicValue())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Object o) {
        return o instanceof List<?> l ? (List<String>) l : null;
    }

    private static String extractTranscript(Answer parentAnswer) {
        if (parentAnswer == null || parentAnswer.getRawEvaluation() == null) return "";
        Object answer = parentAnswer.getRawEvaluation().get("answer");
        if (answer instanceof Map<?, ?> m) {
            Object t = m.get("transcript");
            if (t instanceof String s) return s;
        }
        return "";
    }
}
