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
import com.mockwise.interview.enums.Completeness;
import com.mockwise.interview.enums.Correctness;
import com.mockwise.interview.enums.Depth;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.enums.SignalStrength;
import com.mockwise.interview.enums.TopicKind;
import com.mockwise.interview.enums.TopicStatus;
import com.mockwise.interview.message.constants.KafkaTopics;
import com.mockwise.interview.message.event.SubmissionJudgedEvent;
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
import com.mockwise.interview.dto.response.CodingProblemView;
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
public class AnswerService {

    AnswerRepository answerRepo;
    AnswerEventLogRepository answerEventLogRepo;
    SessionQuestionRepository sessionQuestionRepo;
    SessionTopicStateRepository topicStateRepo;
    InterviewSessionRepository sessionRepo;
    InterviewBlueprintRepository blueprintRepo;

    StorageAdapter storageAdapter;
    UserProfileAdapter userProfileAdapter;
    com.mockwise.interview.client.ttsstt.TtsSttAdapter ttsSttAdapter;
    OutboxWriter outboxWriter;

    AssessmentVerdictMapper verdictMapper;
    NextQuestionPlanner planner;
    SideEffectApplier sideEffectApplier;
    QuestionPicker questionPicker;
    SessionFinalizerService sessionFinalizer;
    SessionService sessionService;
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
        // Server-authoritative time cutoff: if the session clock has run out,
        // finalize it and reject this late submission with SESSION_TIME_UP
        // (instead of silently accepting an answer past the deadline).
        sessionService.ensureWithinTimeBudget(session);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessException(StatusCode.SESSION_NOT_IN_PROGRESS);
        }

        SessionQuestion sq = sessionQuestionRepo.findById(sessionQuestionId)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));
        if (!sq.getSessionId().equals(sessionId)) {
            throw new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION);
        }

        // Pre-flight checks specific to type. For VIDEO we keep the storage
        // metadata around — tts-stt's `answer-submitted` consumer needs the
        // bucket / objectKey to download the file, and we already pay the
        // round-trip to storage-service for ownership verification.
        StorageObjectResponse storageObject = null;
        if (input.type() == AnswerType.VIDEO) {
            storageObject = verifyStorageObject(input.storageObjectId(), userId);
        } else {
            if (input.code() == null || input.code().isBlank()) {
                throw new BusinessException(StatusCode.VALIDATION_ERROR, "code is required for CODE answers");
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

        stageOutbound(answer, sq, session.getUserId(), storageObject);

        // CODING is non-adaptive — the next problem isn't gated on judge /
        // AI like the video flow's planner. Pin it right now (Task.md:
        // "khi user submit … load câu tiếp theo lên lập tức") so the FE's
        // next poll already sees it; scoring runs async in the background.
        if (input.type() == AnswerType.CODE) {
            pinNextCodingIfAny(session, sq);
        }

        return new SubmitAnswerOutput(answer.getId(), answer.getStatus(), answer.getSubmittedAt());
    }

    private StorageObjectResponse verifyStorageObject(UUID storageObjectId, String userId) {
        if (storageObjectId == null) {
            throw new BusinessException(StatusCode.VALIDATION_ERROR, "storageObjectId is required for VIDEO answers");
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
        return object;
    }

    private void stageOutbound(
            Answer answer, SessionQuestion sq, String ownerUserId, StorageObjectResponse storageObject) {
        if (answer.getType() == AnswerType.VIDEO) {
            // tts-stt picks this up. Schema must match
            // tts-stt-service/.../kafka/event/AnswerSubmittedEvent.java —
            // `ownerUserId` is NOT NULL on the stt_job table, and
            // `videoMeta.objectKey` is required for SttOrchestrator to
            // download the bytes from MinIO.
            HashMap<String, Object> payload = new HashMap<>();
            payload.put("answerId", answer.getId().toString());
            payload.put("sessionId", answer.getSessionId().toString());
            payload.put("sessionQuestionId", sq.getId().toString());
            payload.put("questionId", String.valueOf(sq.getQuestionId()));
            payload.put("ownerUserId", ownerUserId);
            payload.put("storageObjectId", answer.getStorageObjectId().toString());
            if (storageObject != null) {
                HashMap<String, Object> videoMeta = new HashMap<>();
                videoMeta.put("bucket", storageObject.bucket());
                videoMeta.put("objectKey", storageObject.objectKey());
                videoMeta.put("contentType", storageObject.contentType());
                videoMeta.put("sizeBytes", storageObject.sizeBytes());
                payload.put("videoMeta", videoMeta);
            }
            outboxWriter.stage(
                    KafkaTopics.ANSWER_SUBMITTED,
                    "ANSWER_SUBMITTED",
                    answer.getId(),
                    payload);
        } else {
            // judge-service picks this up. The payload MUST match
            // judge-service SubmissionEvent {submissionId, language, code,
            // functionMeta, testCases} — the previous shape omitted
            // functionMeta/testCases so the judge could never actually run.
            // Scoring uses the FULL set (hidden + shown); only the FE
            // /coding view drops hidden cases.
            Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();
            Object functionMeta = snap.get("functionMeta");
            List<Map<String, Object>> testCases = judgeTestCases(snap.get("testCases"));
            if (functionMeta == null || testCases.isEmpty()) {
                throw new BusinessException(StatusCode.VALIDATION_ERROR,
                        "coding snapshot is missing functionMeta/testCases — cannot judge");
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("submissionId", answer.getId().toString());
            payload.put("language", String.valueOf(answer.getLanguage()));
            payload.put("code", answer.getCode());
            payload.put("functionMeta", functionMeta);
            payload.put("testCases", testCases);
            outboxWriter.stage(
                    KafkaTopics.CODE_SUBMISSION,
                    "CODE_SUBMISSION",
                    answer.getId(),
                    payload);
        }
    }

    /**
     * Projects the frozen snapshot's test cases into the judge
     * {@code TestCaseDto} shape ({@code id, inputData, expectedOutput}),
     * dropping {@code is_hidden} (the judge runs every case it's given).
     * The whole set — hidden and shown — is sent so the official verdict
     * reflects the real pass rate, not just the sample cases.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> judgeTestCases(Object rawTestCases) {
        if (!(rawTestCases instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(tc -> tc instanceof Map)
                .map(tc -> {
                    Map<String, Object> m = (Map<String, Object>) tc;
                    Map<String, Object> out = new HashMap<>();
                    out.put("id", m.get("id"));
                    out.put("inputData", m.get("inputData"));
                    out.put("expectedOutput", m.get("expectedOutput"));
                    return out;
                })
                .toList();
    }

    /**
     * Pins the next problem of a CODING session straight away. Reads the
     * frozen {@code metadata.codingPlan} list and materialises the
     * {@code SessionQuestion} for {@code currentSq.sequence + 1}, unless
     * that's already pinned (double-submit) or the plan is exhausted (the
     * just-submitted question was the last one — the session finalises
     * after its judge/AI verdict lands).
     */
    @SuppressWarnings("unchecked")
    private void pinNextCodingIfAny(InterviewSession session, SessionQuestion currentSq) {
        Map<String, Object> meta = session.getMetadata();
        if (meta == null || !(meta.get("codingPlan") instanceof List<?> plan) || plan.isEmpty()) {
            log.warn("CODING session {} has no codingPlan in metadata — cannot pin next", session.getId());
            return;
        }
        int nextSequence = currentSq.getSequence() + 1;
        int planIdx = currentSq.getSequence(); // plan is 0-based; sequence is 1-based
        if (planIdx >= plan.size()) {
            log.info("CODING session {} — submitted the last question (seq {}), nothing to pin",
                    session.getId(), currentSq.getSequence());
            return;
        }
        boolean alreadyPinned = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(session.getId())
                .stream().anyMatch(q -> q.getSequence() == nextSequence);
        if (alreadyPinned) {
            log.debug("CODING session {} seq {} already pinned — skipping (double submit?)",
                    session.getId(), nextSequence);
            return;
        }
        Map<String, Object> entry = (Map<String, Object>) plan.get(planIdx);
        SessionQuestion next = questionPicker.pinCoding(session.getId(), entry, nextSequence);
        log.info("CODING session {} — pinned next question {} (seq {})",
                session.getId(), next.getId(), nextSequence);
    }

    // ── Get one answer (FE poll) ─────────────────────────────────────────────

    /**
     * Bundles an answer with the parent session's status and the parent
     * session_question's type. The FE poll endpoint needs the session status
     * to decide whether to expose the per-question verdict (revealed only
     * after the session reaches SCORED), and the question type to pick the
     * right {@code EvaluationDetail} variant when projecting {@code raw_evaluation}.
     * We already have to load the session for the ownership check — return
     * the bundle so the controller doesn't issue a second query.
     */
    public record AnswerWithSession(
            Answer answer,
            SessionStatus sessionStatus,
            com.mockwise.interview.enums.QuestionType questionType) {}

    /**
     * Returns the answer plus its session's current status if the caller owns
     * the session. The FE polls this to render scoring → ready → scored
     * transitions; SSE will subsume it in Phase G but this is the deterministic
     * fallback.
     */
    @Transactional(readOnly = true)
    public AnswerWithSession getForUser(UUID sessionId, UUID answerId, String userId) {
        Answer answer = answerRepo.findById(answerId)
                .orElseThrow(() -> new BusinessException(StatusCode.ANSWER_NOT_FOUND));
        if (!answer.getSessionId().equals(sessionId)) {
            throw new BusinessException(StatusCode.ANSWER_NOT_FOUND);
        }
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(StatusCode.SESSION_NOT_OWNER);
        }
        // Question type drives EvaluationDetail variant. We load the session_question
        // here (instead of forcing the controller to do it) so the call site stays
        // a one-liner.
        com.mockwise.interview.enums.QuestionType qType = sessionQuestionRepo
                .findById(answer.getSessionQuestionId())
                .map(SessionQuestion::getQuestionType)
                .orElse(null);
        return new AnswerWithSession(answer, session.getStatus(), qType);
    }

    // ── Get the LIVE_CODING problem for the in-flight FE workspace ───────────

    /**
     * Serves the coding problem for one pinned LIVE_CODING question,
     * straight from the frozen snapshot. Owner-gated; 409 if the question
     * isn't a coding one. Hidden test cases are stripped inside
     * {@link CodingProblemView#fromSessionQuestion} so they never leave the
     * backend.
     */
    @Transactional(readOnly = true)
    public CodingProblemView getCodingProblem(UUID sessionId, UUID sessionQuestionId, String userId) {
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(StatusCode.SESSION_NOT_OWNER);
        }
        SessionQuestion sq = sessionQuestionRepo.findById(sessionQuestionId)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));
        if (!sq.getSessionId().equals(sessionId)) {
            throw new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION);
        }
        if (sq.getQuestionType() != QuestionType.LIVE_CODING) {
            throw new BusinessException(StatusCode.QUESTION_NOT_CODING);
        }
        return CodingProblemView.fromSessionQuestion(sq);
    }

    // ── Apply transcript-ready / -failed (tts-stt → orchestrator) ────────────

    /**
     * Called by the {@code transcript-ready} consumer. Flips the answer
     * from PROCESSING → READY → EVALUATING (in one Tx) and stages the
     * {@code evaluation-requested} payload to the outbox so the AI
     * service has a self-contained input — no callback needed.
     *
     * <p>The eval payload shape matches what {@code AI/api.py POST /evaluate}
     * accepts: BehavioralInput / ConceptualInput. Coding answers don't
     * land here — they go through the judge-service path.
     *
     * <p>Idempotency: an answer already past PROCESSING is a no-op return.
     * The consumer's own dedup ({@code processed_event}) is the primary
     * guard but we belt-and-brace here so a manual replay is safe.
     */
    @Transactional
    public void applyTranscriptReady(
            UUID answerId, UUID transcriptId, String languageCode,
            String transcriptText, Integer durationMs) {

        Answer answer = answerRepo.findById(answerId)
                .orElseThrow(() -> new BusinessException(StatusCode.ANSWER_NOT_FOUND));
        if (answer.getStatus() != AnswerStatus.PROCESSING) {
            log.debug("Answer {} status is {} — ignoring late transcript-ready",
                    answerId, answer.getStatus());
            return;
        }

        SessionQuestion sq = sessionQuestionRepo.findById(answer.getSessionQuestionId())
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));

        // Step 1 — resolve transcript text + duration. The event now
        // carries the text inline, so the common path needs no REST hop.
        // Only legacy events replayed from before that field existed fall
        // back to GET /internal/transcripts/{id}.
        String text;
        Integer durMs;
        if (transcriptText != null && !transcriptText.isBlank()) {
            text = transcriptText;
            durMs = durationMs;
        } else {
            var transcript = ttsSttAdapter.getTranscript(transcriptId.toString());
            text = transcript.text();
            durMs = transcript.durationMs();
        }

        // Step 2 — answer rows transition: PROCESSING → READY → EVALUATING
        // in one Tx. We collapse READY into EVALUATING because the
        // evaluation-requested outbox row goes out in this same write —
        // there's no observable READY tick from outside.
        AnswerStatus prev = answer.getStatus();
        answer.setStatus(AnswerStatus.EVALUATING);
        answer.setTranscriptId(transcriptId);
        answerRepo.save(answer);
        logTransition(answerId, prev, AnswerStatus.EVALUATING, "transcript-ready", Map.of(
                "transcriptId", transcriptId.toString(),
                "languageCode", String.valueOf(languageCode)));

        // Step 3 — assemble evaluation-requested. Field names match
        // AI/models/inputs.py exactly so the payload deserialises into
        // BehavioralInput / ConceptualInput on the Python side.
        Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();

        // Prefer the candidate's profile language for the AI's response —
        // STT auto-detect is a hint about what the candidate SPOKE, but the
        // feedback should land in the language the candidate set on their
        // profile (e.g. a Vietnamese candidate who says "let me think" mid-
        // answer still wants the writeup in Vietnamese). Fallback chain:
        //   profile.preferredLanguage  →  STT detected language  →  "vi"
        InterviewSession session = sessionRepo.findById(answer.getSessionId())
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        String preferred = null;
        try {
            UserProfileResponse profile = loadProfile(session);
            if (profile != null && profile.preferredLanguage() != null) {
                preferred = profile.preferredLanguage().toLowerCase();
            }
        } catch (Exception ex) {
            log.warn("Failed to load profile for response-language hint, falling back to STT: {}",
                    ex.getMessage());
        }
        String responseLanguage;
        if ("vi".equals(preferred) || "en".equals(preferred)) {
            responseLanguage = preferred;
        } else if (languageCode != null) {
            responseLanguage = languageCode.toLowerCase().contains("vi") ? "vi" : "en";
        } else {
            responseLanguage = "vi";
        }

        // Field names are camelCase to match the AI service's Pydantic
        // CamelModel base — the AI side accepts both (populate_by_name=True)
        // but emits camelCase, so producing camelCase here keeps the wire
        // format symmetric and consistent with the rest of Mockwise.
        Map<String, Object> question = new HashMap<>();
        question.put("id", sq.getQuestionId());
        question.put("text", sq.getInlineText() != null ? sq.getInlineText() : snap.get("text"));
        if (sq.getQuestionType() == QuestionType.BEHAVIORAL) {
            question.put("competency", snap.get("competency"));
            question.put("expectedSignals", snap.getOrDefault("expectedSignals", List.of()));
        } else {
            question.put("domain", snap.get("domain"));
            question.put("keyConcepts", snap.getOrDefault("keyConcepts", List.of()));
            question.put("depthExpected", snap.getOrDefault("depthExpected", "intermediate"));
        }

        Map<String, Object> answerBlock = new HashMap<>();
        answerBlock.put("transcript", text != null ? text : "");
        answerBlock.put("durationSeconds", durMs != null ? durMs / 1000 : 0);
        answerBlock.put("language", responseLanguage);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", answer.getSessionId().toString());
        payload.put("interviewType",
                sq.getQuestionType() == QuestionType.BEHAVIORAL ? "behavioral" : "core_conceptual");
        payload.put("question", question);
        payload.put("answer", answerBlock);
        payload.put("responseLanguage", responseLanguage);

        // Wrap in the EvaluationRequestedEvent shape the AI consumer expects
        // (AI/models/evaluation_events.py).
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", java.util.UUID.randomUUID().toString());
        envelope.put("eventType", "EVALUATION_REQUESTED");
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("answerId", answer.getId().toString());
        envelope.put("sessionId", answer.getSessionId().toString());
        envelope.put("questionId", String.valueOf(sq.getQuestionId()));
        envelope.put("payload", payload);

        outboxWriter.stage(
                com.mockwise.interview.message.constants.KafkaTopics.EVALUATION_REQUESTED,
                "EVALUATION_REQUESTED",
                answer.getId(),
                envelope);
    }

    /**
     * Called by the {@code transcript-failed} consumer. Flips the answer
     * to FAILED with the upstream error so the FE can render
     * "couldn't process — please re-record".
     */
    @Transactional
    public void applyTranscriptFailed(UUID answerId, String errorCode, String errorMessage) {
        applyFailureTerminalState(answerId, errorCode, errorMessage, "transcript-failed");
    }

    /**
     * Called by the {@code evaluation-failed} consumer. Same terminal
     * flip as transcript-failed — operator can replay later.
     */
    @Transactional
    public void applyEvaluationFailed(UUID answerId, String errorCode, String errorMessage) {
        applyFailureTerminalState(answerId, errorCode, errorMessage, "evaluation-failed");
    }

    private void applyFailureTerminalState(UUID answerId, String errorCode, String errorMessage, String reason) {
        Answer answer = answerRepo.findById(answerId)
                .orElseThrow(() -> new BusinessException(StatusCode.ANSWER_NOT_FOUND));
        if (answer.getStatus() == AnswerStatus.SCORED || answer.getStatus() == AnswerStatus.FAILED) {
            log.debug("Answer {} already terminal ({}) — ignoring {}",
                    answerId, answer.getStatus(), reason);
            return;
        }
        AnswerStatus prev = answer.getStatus();
        answer.setStatus(AnswerStatus.FAILED);
        answer.setErrorCode(errorCode);
        answer.setErrorMessage(errorMessage);
        answer.setScoredAt(OffsetDateTime.now());
        answerRepo.save(answer);
        logTransition(answerId, prev, AnswerStatus.FAILED, reason, Map.of(
                "errorCode", String.valueOf(errorCode),
                "errorMessage", String.valueOf(errorMessage)));

        // After flipping the answer terminal, the session may now be ready
        // for the cross-question overall review. Idempotent — only fires
        // when COMPLETED + every answer terminal.
        sessionFinalizer.maybeRequestOverallReview(answer.getSessionId());
    }

    // ── Apply submission-judged (judge-service → orchestrator) ───────────────

    /**
     * Called by the {@code submission-judged} consumer for a CODE answer.
     * Spam-guard (Task.md / user decision): if the code never actually ran
     * — judge reports only CE/RE (compile error, build/syntax/runtime fail)
     * or there were no test cases — it's an incomplete / garbage submission;
     * skip the AI round-trip, record a cheap terminal verdict and let the
     * session finalise. Otherwise (something compiled and ran — AC/WA/TLE/
     * MLE present) forward to the AI {@code live_coding} evaluator; the
     * existing {@code evaluation-completed} path + {@code deriveVerdict}
     * finishes scoring exactly like the video flow.
     *
     * <p>Idempotent: an answer not in PROCESSING is a no-op (covers a
     * duplicate judge delivery or a manual replay).
     */
    @Transactional
    public void applyCodeJudged(UUID answerId, String verdict,
                                List<SubmissionJudgedEvent.CaseResult> results) {
        Answer answer = answerRepo.findById(answerId)
                .orElseThrow(() -> new BusinessException(StatusCode.ANSWER_NOT_FOUND));
        if (answer.getStatus() != AnswerStatus.PROCESSING) {
            log.debug("Answer {} status is {} — ignoring late submission-judged",
                    answerId, answer.getStatus());
            return;
        }
        SessionQuestion sq = sessionQuestionRepo.findById(answer.getSessionQuestionId())
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));
        InterviewSession session = sessionRepo.findById(answer.getSessionId())
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));

        List<SubmissionJudgedEvent.CaseResult> cases =
                results != null ? results : List.<SubmissionJudgedEvent.CaseResult>of();
        int total = cases.size();
        int passed = (int) cases.stream()
                .filter(c -> "AC".equalsIgnoreCase(c.status()))
                .count();
        boolean ranAtAll = cases.stream().anyMatch(c -> {
            String st = c.status() == null ? "" : c.status().toUpperCase();
            return st.equals("AC") || st.equals("WA") || st.equals("TLE") || st.equals("MLE");
        });
        boolean blankCode = answer.getCode() == null || answer.getCode().isBlank();

        // Compact per-case roster (id + status only — no stdin/stdout so hidden
        // testcase data never lands in the answer row) so the report can list
        // which cases passed. Read back by AnswerView once SCORED.
        List<Map<String, Object>> caseList = cases.stream()
                .<Map<String, Object>>map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("testCaseId", c.testCaseId());
                    m.put("status", c.status());
                    return m;
                })
                .toList();

        if (blankCode || total == 0 || !ranAtAll) {
            AssessmentVerdict cheap = new AssessmentVerdict(
                    0.0f, null, null,
                    SignalStrength.NONE,
                    Completeness.NO_ANSWER,
                    Correctness.WRONG,
                    Depth.SURFACE,
                    List.of(), List.of());
            AnswerStatus prev = answer.getStatus();
            answer.setStatus(AnswerStatus.SCORED);
            answer.setScoredAt(OffsetDateTime.now());
            answer.setScore(0.0f);
            answer.setMaxScore(10.0f);
            answer.setVerdict(verdictToMap(cheap));
            Map<String, Object> raw = new HashMap<>();
            raw.put("skipped", true);
            raw.put("skippedReason",
                    "non-runnable code (judge verdict " + verdict + ") — AI evaluation skipped");
            raw.put("judgeVerdict", verdict);
            raw.put("testsTotal", total);
            raw.put("testsPassed", passed);
            raw.put("cases", caseList);
            answer.setRawEvaluation(raw);
            answerRepo.save(answer);
            logTransition(answerId, prev, AnswerStatus.SCORED, "code-judged-skipped", Map.of(
                    "judgeVerdict", String.valueOf(verdict),
                    "testsPassed", passed + "/" + total));
            log.info("Answer {} CODE judged non-runnable (verdict={}) — skipped AI, cheap verdict",
                    answerId, verdict);
            sessionFinalizer.maybeRequestOverallReview(session.getId());
            return;
        }

        AnswerStatus prev = answer.getStatus();
        answer.setStatus(AnswerStatus.EVALUATING);
        Map<String, Object> judgeSummary = new HashMap<>();
        judgeSummary.put("verdict", String.valueOf(verdict));
        judgeSummary.put("testsTotal", total);
        judgeSummary.put("testsPassed", passed);
        judgeSummary.put("cases", caseList);
        answer.setRubricScores(new HashMap<>(Map.of("judge", judgeSummary)));
        answerRepo.save(answer);
        logTransition(answerId, prev, AnswerStatus.EVALUATING, "code-judged", Map.of(
                "judgeVerdict", String.valueOf(verdict),
                "testsPassed", passed + "/" + total));

        stageCodingEvaluationRequested(answer, sq, session, total, passed);
        log.info("Answer {} CODE judged runnable ({} / {} passed) — requested AI live_coding eval",
                answerId, passed, total);
    }

    /**
     * Stages an {@code evaluation-requested} outbox event whose payload is
     * the AI {@code LiveCodingInput} shape ({@code AI/models/inputs.py}).
     * Same envelope as the video path's {@code applyTranscriptReady} so the
     * AI consumer routes on {@code interview_type == "live_coding"}.
     */
    private void stageCodingEvaluationRequested(
            Answer answer, SessionQuestion sq, InterviewSession session, int total, int passed) {

        Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();

        String responseLanguage = "vi";
        try {
            UserProfileResponse profile = loadProfile(session);
            if (profile != null && profile.preferredLanguage() != null) {
                String pref = profile.preferredLanguage().toLowerCase();
                if ("vi".equals(pref) || "en".equals(pref)) {
                    responseLanguage = pref;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to load profile for coding response-language hint: {}", ex.getMessage());
        }

        // The AI LiveCodingInput schema requires these as non-null strings.
        // A question whose optimal-complexity / title / description columns
        // are empty must not produce a null here, or the AI router rejects
        // the whole payload (input_validation_failed → evaluation-failed →
        // answer FAILED, never scored). Fall back to safe placeholders.
        Map<String, Object> optimal = new HashMap<>();
        optimal.put("time", strOr(snap.get("optimalTimeComplexity"), "unknown"));
        optimal.put("space", strOr(snap.get("optimalSpaceComplexity"), "unknown"));

        Map<String, Object> question = new HashMap<>();
        question.put("id", String.valueOf(sq.getQuestionId()));
        question.put("title", strOr(snap.get("title"), "Coding Problem"));
        question.put("description", strOr(snap.get("description"), ""));
        question.put("difficulty", sq.getDifficulty() != null ? sq.getDifficulty().name() : "MEDIUM");
        question.put("tags", snap.getOrDefault("tags", List.of()));
        question.put("constraints", strOr(snap.get("constraints"), ""));
        question.put("optimalComplexity", optimal);

        Map<String, Object> testSummary = new HashMap<>();
        testSummary.put("total", total);
        testSummary.put("passed", passed);

        Map<String, Object> submission = new HashMap<>();
        submission.put("code", answer.getCode() != null ? answer.getCode() : "");
        submission.put("language", String.valueOf(answer.getLanguage()));
        submission.put("timeSpentMinutes", computeTimeSpentMinutes(sq, answer));
        submission.put("testSummary", testSummary);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", answer.getSessionId().toString());
        payload.put("interviewType", "live_coding");
        payload.put("question", question);
        payload.put("submission", submission);
        payload.put("responseLanguage", responseLanguage);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "EVALUATION_REQUESTED");
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("answerId", answer.getId().toString());
        envelope.put("sessionId", answer.getSessionId().toString());
        envelope.put("questionId", String.valueOf(sq.getQuestionId()));
        envelope.put("payload", payload);

        outboxWriter.stage(
                KafkaTopics.EVALUATION_REQUESTED,
                "EVALUATION_REQUESTED",
                answer.getId(),
                envelope);
    }

    /** Non-null string projection: blank/null snapshot value → {@code fallback}. */
    private static String strOr(Object value, String fallback) {
        if (value == null) return fallback;
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    private static int computeTimeSpentMinutes(SessionQuestion sq, Answer answer) {
        try {
            if (sq.getCreatedAt() != null && answer.getSubmittedAt() != null) {
                long secs = java.time.Duration
                        .between(sq.getCreatedAt(), answer.getSubmittedAt()).getSeconds();
                return (int) Math.max(0, Math.min(secs / 60, 24L * 60));
            }
        } catch (Exception ignored) {
            // best-effort metadata only
        }
        return 0;
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
            // Still check the gate — a session with only coding answers will
            // never trip the planner-driven EndSession but its answers can
            // all reach SCORED via the manual /finish path.
            sessionFinalizer.maybeRequestOverallReview(session.getId());
            return;
        }
        runPlannerForAnswer(session, sq, verdict);

        // Planner may have flipped the session to COMPLETED via EndSession.
        // If so (or if a sibling answer's path already did), kick off the
        // overall review. Idempotent — only fires once per session.
        sessionFinalizer.maybeRequestOverallReview(session.getId());
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
        UserProfileResponse profile = loadProfile(session);
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
        UserProfileResponse profile = loadProfile(session);
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

    /**
     * Returns the candidate's profile without a REST hop when possible:
     * {@code SessionService.start} snapshots it into
     * {@code session.metadata.profileSnapshot}, and the profile barely
     * changes over a ~20-min session. Falls back to a live
     * user-profile-service fetch for sessions created before the
     * snapshot existed, or if the blob is malformed.
     *
     * <p>Stale risk is limited to {@code preferredLanguage} drifting if
     * the user edits their profile mid-session — accepted: the AI still
     * picks a sensible language from the STT-detected hint.
     */
    private UserProfileResponse loadProfile(InterviewSession session) {
        Map<String, Object> meta = session.getMetadata();
        if (meta != null && meta.get("profileSnapshot") instanceof Map<?, ?> snap) {
            try {
                return objectMapper.convertValue(snap, UserProfileResponse.class);
            } catch (IllegalArgumentException ex) {
                log.warn("Session {} has malformed profileSnapshot — refetching from user-profile",
                        session.getId(), ex);
            }
        }
        return userProfileAdapter.getProfile(session.getUserId());
    }

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
