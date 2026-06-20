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
import com.mockwise.interview.enums.InterviewType;
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
import com.mockwise.interview.dto.response.PinnedQuestionView;
import com.mockwise.interview.dto.response.SubmitAnswerOutput;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // Lever 2 kill-switch (realtime-stt-plan.md §9). OFF → the real-time
    // transcript on a VIDEO submit is ignored and every answer runs the legacy
    // upload-then-batch-STT flow. @NonFinal so Lombok's @RequiredArgsConstructor
    // doesn't pull it into the constructor — Spring sets it via @Value field
    // injection (the @Value + @RequiredArgsConstructor gotcha).
    @NonFinal
    @Value("${interview.realtime-stt.enabled:true}")
    boolean realtimeSttEnabled;

    // ── Realtime STT token (Lever 2 fast path) ───────────────────────────────

    /**
     * Mints a single-use ElevenLabs realtime Scribe token for the browser
     * (realtime-stt-plan.md §5 Provider C). Gated by the same kill-switch as the
     * fast-path submit: when OFF we refuse so the FE falls back to the legacy
     * upload-then-batch-STT flow even if its own flag is mistakenly on. The
     * userId is required so only an authenticated caller can spend tokens.
     */
    public com.mockwise.interview.client.ttsstt.dto.RealtimeSttTokenResponse mintRealtimeSttToken(String userId) {
        if (!realtimeSttEnabled) {
            throw new BusinessException(StatusCode.REALTIME_STT_DISABLED);
        }
        return ttsSttAdapter.mintRealtimeToken();
    }

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
        // Lever 2 fast path (realtime-stt-plan.md §6.2): a VIDEO answer that
        // arrives with a real-time transcript scores straight off it — no wait
        // for the video upload or batch STT. The clip uploads in the background
        // and attaches later via POST .../attach-video. Gated by the
        // kill-switch; a blank transcript (flaky mic / lost network) falls
        // through to the legacy upload-then-batch-STT flow (§8.1 #1).
        //
        // Checked BEFORE the dedup throw so this path can be idempotent: a
        // retried submit (lost 200 / double-fire) gets back the existing
        // answerId instead of a 409, so the FE can still attach its background
        // video to the right answer (§8.4 #11).
        if (input.type() == AnswerType.VIDEO
                && realtimeSttEnabled
                && input.transcriptText() != null
                && !input.transcriptText().isBlank()) {
            return submitWithRealtimeTranscript(session, sq, input, userId);
        }

        // One answer per session_question (the planner runs once per question;
        // a duplicate would double-score a topic / over-count the budget). A
        // reload-during-waiting or a retried submit lands here — reject cleanly
        // so the FE can treat it as "already submitted" and resume waiting.
        if (answerRepo.existsBySessionQuestionId(sessionQuestionId)) {
            throw new BusinessException(StatusCode.ANSWER_ALREADY_SUBMITTED);
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
        // "khi user submit … load câu tiếp theo lên lập tức") and return it so
        // the FE advances immediately (no poll). Scoring runs async in the
        // background. For VIDEO the next question is planner-gated → null.
        PinnedQuestionView nextQuestion = null;
        if (input.type() == AnswerType.CODE) {
            pinNextCodingIfAny(session, sq);
            nextQuestion = loadNextPinnedRedacted(session.getId(), sq.getSequence() + 1);
        }

        return new SubmitAnswerOutput(
                answer.getId(), answer.getStatus(), answer.getSubmittedAt(), nextQuestion);
    }

    /**
     * Lever 2 fast path. Persists a VIDEO answer straight into EVALUATING off
     * the client's real-time transcript and stages {@code evaluation-requested}
     * immediately — skipping the upload wait and the batch-STT hop entirely.
     *
     * <p>The video object is NOT persisted here even when the FE passes its
     * in-flight id: it's still uploading, so {@code storage_object_id} stays
     * null until {@code attachVideo} wires it up (which is also the single
     * place that re-verifies READY and triggers the background batch STT). That
     * keeps "video attached yet?" unambiguous for idempotency.
     */
    private SubmitAnswerOutput submitWithRealtimeTranscript(
            InterviewSession session, SessionQuestion sq, SubmitAnswerInput input, String userId) {

        // Idempotent retry (§8.4 #11): a re-fired fast-path submit (lost 200 /
        // double-submit) returns the existing answer so the FE keeps the right
        // answerId for its background attach-video — no 409, no second row.
        var existing = answerRepo.findBySessionQuestionId(sq.getId());
        if (existing.isPresent()) {
            Answer a = existing.get();
            return new SubmitAnswerOutput(a.getId(), a.getStatus(), a.getSubmittedAt(), null);
        }

        // Optional pre-flight: if the FE passed the in-flight upload id, fail
        // fast on a wrong-owner / wrong-kind object — but allow a non-READY
        // (PENDING) status since the clip is still streaming up.
        if (input.storageObjectId() != null) {
            verifyStorageOwnership(input.storageObjectId(), userId);
        }

        String source = input.transcriptSource() != null && !input.transcriptSource().isBlank()
                ? input.transcriptSource() : "REALTIME_WEBSPEECH";

        Answer answer = answerRepo.save(Answer.builder()
                .sessionId(session.getId())
                .sessionQuestionId(sq.getId())
                .type(AnswerType.VIDEO)
                .status(AnswerStatus.EVALUATING)  // skip PROCESSING/READY — we score now
                .realtimeTranscript(input.transcriptText())
                .transcriptSource(source)
                .submittedAt(OffsetDateTime.now())
                .build());

        logTransition(answer.getId(), null, AnswerStatus.EVALUATING, "realtime-transcript-submitted", Map.of(
                "transcriptSource", source,
                "transcriptChars", String.valueOf(input.transcriptText().length())));

        // Log the actual transcript text the FE sent so we can confirm the
        // realtime fast path is producing usable content (grep "REALTIME
        // TRANSCRIPT" on the VPS). Capped so a long answer doesn't flood logs.
        log.info("REALTIME TRANSCRIPT received answerId={} session={} sq={} source={} lang={} durationMs={} chars={} text=\"{}\"",
                answer.getId(), session.getId(), sq.getId(), source,
                input.transcriptLanguage(), input.transcriptDurationMs(),
                input.transcriptText().length(), transcriptPreview(input.transcriptText()));

        stageSpokenEvaluation(answer, sq, session, input.transcriptText(),
                input.transcriptDurationMs(), input.transcriptLanguage());

        // VIDEO is planner-gated — the next question is pinned async once the
        // AI verdict lands. Nothing to return synchronously.
        return new SubmitAnswerOutput(
                answer.getId(), answer.getStatus(), answer.getSubmittedAt(), null);
    }

    /**
     * Builds the {@code evaluation-requested} envelope for a spoken
     * (BEHAVIORAL / CORE_CONCEPTUAL) answer and stages it to the outbox. Shared
     * by the Lever 2 fast path ({@link #submitWithRealtimeTranscript}) and the
     * legacy batch-STT path ({@link #applyTranscriptReadyLegacy}) so the AI
     * payload is identical regardless of where the transcript came from.
     *
     * <p>Does not touch the answer's status — the caller owns the transition.
     * Field names are camelCase to match the AI service's Pydantic CamelModel.
     */
    private void stageSpokenEvaluation(
            Answer answer, SessionQuestion sq, InterviewSession session,
            String transcriptText, Integer durationMs, String spokenLanguageHint) {

        Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();

        // Prefer the candidate's profile language for the AI's response — the
        // spoken-language hint just says what they SPOKE; feedback should land
        // in the language they set on their profile. Fallback chain:
        //   profile.preferredLanguage → spoken hint → "vi".
        String preferred = null;
        try {
            UserProfileResponse profile = loadProfile(session);
            if (profile != null && profile.preferredLanguage() != null) {
                preferred = profile.preferredLanguage().toLowerCase();
            }
        } catch (Exception ex) {
            log.warn("Failed to load profile for response-language hint, falling back to spoken hint: {}",
                    ex.getMessage());
        }
        String responseLanguage;
        if ("vi".equals(preferred) || "en".equals(preferred)) {
            responseLanguage = preferred;
        } else if (spokenLanguageHint != null) {
            responseLanguage = spokenLanguageHint.toLowerCase().contains("vi") ? "vi" : "en";
        } else {
            responseLanguage = "vi";
        }

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
        answerBlock.put("transcript", transcriptText != null ? transcriptText : "");
        answerBlock.put("durationSeconds", durationMs != null ? durationMs / 1000 : 0);
        answerBlock.put("language", responseLanguage);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", answer.getSessionId().toString());
        payload.put("interviewType",
                sq.getQuestionType() == QuestionType.BEHAVIORAL ? "behavioral" : "core_conceptual");
        payload.put("question", question);
        payload.put("answer", answerBlock);
        payload.put("responseLanguage", responseLanguage);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", java.util.UUID.randomUUID().toString());
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

    /**
     * Loads the just-pinned next coding question (by sequence) as a redacted
     * view, or null if the plan is exhausted (last problem submitted). Coding
     * questions carry no audio, so no URL signing is needed.
     */
    private PinnedQuestionView loadNextPinnedRedacted(UUID sessionId, int sequence) {
        return sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(sessionId).stream()
                .filter(q -> q.getSequence() == sequence)
                .findFirst()
                .map(PinnedQuestionView::fromEntity)
                .map(PinnedQuestionView::redacted)
                .orElse(null);
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

    /**
     * Lighter sibling of {@link #verifyStorageObject} for the Lever 2 fast
     * path: checks ownership + kind but tolerates a non-READY status, because
     * the clip is still uploading when {@code submit} runs. Full READY
     * verification happens later in {@link #attachVideo}.
     */
    private void verifyStorageOwnership(UUID storageObjectId, String userId) {
        StorageObjectResponse object;
        try {
            object = storageAdapter.getObject(storageObjectId);
        } catch (BusinessException e) {
            if (e.getHttpStatus() == 404) {
                throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_READY);
            }
            throw e;
        }
        if (!"INTERVIEW_VIDEO".equals(object.kind())) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_WRONG_KIND);
        }
        if (!userId.equals(object.ownerUserId())) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_OWNER_MISMATCH);
        }
    }

    /**
     * Lever 2 (realtime-stt-plan.md §6.2.3). FE calls this once the background
     * video upload composes, after a fast-path submit. Wires the (now READY)
     * object onto the already-scored answer and stages {@code answer-submitted}
     * so tts-stt runs batch STT for the authoritative transcript.
     *
     * <p>Deliberately skips the {@code ensureWithinTimeBudget} /
     * {@code IN_PROGRESS} guards that {@link #submit} uses (§8.2 #7): the
     * upload can finish after the session clock is up or after the session is
     * COMPLETED, and rejecting it then would silently drop the video. Ownership
     * is the only gate.
     *
     * <p>Idempotent (§8.2 #12): a second attach (FE double-finish / retry) is a
     * no-op so batch STT never fires twice.
     */
    @Transactional
    public void attachVideo(UUID sessionId, UUID answerId, UUID storageObjectId, String userId) {
        if (storageObjectId == null) {
            throw new BusinessException(StatusCode.VALIDATION_ERROR, "storageObjectId is required");
        }
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(StatusCode.SESSION_NOT_OWNER);
        }
        Answer answer = answerRepo.findById(answerId)
                .orElseThrow(() -> new BusinessException(StatusCode.ANSWER_NOT_FOUND));
        if (!answer.getSessionId().equals(sessionId)) {
            throw new BusinessException(StatusCode.ANSWER_NOT_FOUND);
        }
        if (answer.getStorageObjectId() != null) {
            log.debug("Answer {} already has a video attached — ignoring duplicate attach-video", answerId);
            return;
        }

        StorageObjectResponse storageObject = verifyStorageObject(storageObjectId, userId);
        answer.setStorageObjectId(storageObjectId);
        answerRepo.save(answer);

        SessionQuestion sq = sessionQuestionRepo.findById(answer.getSessionQuestionId())
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));
        // Background record only — the answer is already EVALUATING/SCORED off
        // the real-time transcript. tts-stt's transcript-ready will be applied
        // as the authoritative transcript (never re-scores) via applyTranscriptReady.
        stageOutbound(answer, sq, session.getUserId(), storageObject);
        logTransition(answerId, answer.getStatus(), answer.getStatus(), "video-attached", Map.of(
                "storageObjectId", storageObjectId.toString()));
        log.info("Answer {} attached background video {} — staged answer-submitted for batch STT",
                answerId, storageObjectId);
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
     * Called by the {@code transcript-ready} consumer. The meaning depends on
     * the answer's current status (realtime-stt-plan.md §6.2.4 / §8.2 #5):
     *
     * <ul>
     *   <li>{@code PROCESSING} → legacy batch-STT flow: flip
     *       PROCESSING → EVALUATING and stage {@code evaluation-requested} so
     *       the AI scores this transcript. (The eval payload shape matches
     *       {@code AI/api.py POST /evaluate}: BehavioralInput / ConceptualInput.)</li>
     *   <li>{@code EVALUATING | SCORED} → Lever 2 fast path already scored this
     *       answer off the real-time transcript; the batch transcript arriving
     *       now is the <em>authoritative</em> record. Store it for
     *       display/reconciliation only — never re-score, never re-plan
     *       (idempotent against late delivery + finalize races, §8.2 #6).</li>
     *   <li>anything else (SUBMITTED / READY / FAILED) → ignore. A FAILED answer
     *       could in principle be rescued from batch, but we leave that to an
     *       operator replay for now.</li>
     * </ul>
     *
     * <p>⚠️ Do NOT reintroduce a single {@code if (status != PROCESSING) return}
     * early-return here — that silently discarded the authoritative transcript
     * on the fast path (the regression §8.2 #5 calls out). The {@code switch} is
     * load-bearing.
     *
     * <p>Idempotency: the consumer's {@code processed_event} dedup is the
     * primary guard; the per-status branches are individually idempotent so a
     * manual replay stays safe. Coding answers don't land here — they go
     * through the judge-service path.
     */
    @Transactional
    public void applyTranscriptReady(
            UUID answerId, UUID transcriptId, String languageCode,
            String transcriptText, Integer durationMs) {

        Answer answer = answerRepo.findById(answerId)
                .orElseThrow(() -> new BusinessException(StatusCode.ANSWER_NOT_FOUND));

        // Resolve transcript text + duration once. The event carries the text
        // inline on the common path; only legacy events replayed from before
        // that field existed fall back to GET /internal/transcripts/{id}.
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

        switch (answer.getStatus()) {
            case PROCESSING ->
                    applyTranscriptReadyLegacy(answer, transcriptId, languageCode, text, durMs);
            case EVALUATING, SCORED ->
                    attachAuthoritativeTranscript(answer, transcriptId, text);
            default ->
                    log.debug("Answer {} status is {} — ignoring transcript-ready "
                            + "(no legacy scoring / authoritative action applies)",
                            answerId, answer.getStatus());
        }
    }

    /**
     * Legacy batch-STT scoring path. Flips PROCESSING → EVALUATING (READY is
     * collapsed in — the evaluation-requested outbox row goes out in this same
     * write, so there's no observable READY tick) and stages the AI request.
     * The batch transcript is also the scoring transcript here, so it's stored
     * as {@code authoritativeTranscript} (and {@code transcriptSource =
     * BATCH_SCRIBE}) — that's what a follow-up reads back via
     * {@link #extractTranscript}.
     */
    private void applyTranscriptReadyLegacy(
            Answer answer, UUID transcriptId, String languageCode, String text, Integer durMs) {

        SessionQuestion sq = sessionQuestionRepo.findById(answer.getSessionQuestionId())
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_IN_SESSION));
        InterviewSession session = sessionRepo.findById(answer.getSessionId())
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));

        AnswerStatus prev = answer.getStatus();
        answer.setStatus(AnswerStatus.EVALUATING);
        answer.setTranscriptId(transcriptId);
        answer.setTranscriptSource("BATCH_SCRIBE");
        answer.setAuthoritativeTranscript(text);
        answerRepo.save(answer);
        logTransition(answer.getId(), prev, AnswerStatus.EVALUATING, "transcript-ready", Map.of(
                "transcriptId", transcriptId.toString(),
                "languageCode", String.valueOf(languageCode)));

        stageSpokenEvaluation(answer, sq, session, text, durMs, languageCode);
    }

    /**
     * Fast-path reconciliation (realtime-stt-plan.md §8.1 #3 / §8.2 #5). The
     * answer already scored off the real-time transcript; record the batch
     * transcript as authoritative for display + comparison and flag a material
     * divergence for an admin. Never changes status, score, planner state, or
     * stages any event — late delivery after the session is finalized must be a
     * pure no-op beyond this write.
     */
    private void attachAuthoritativeTranscript(Answer answer, UUID transcriptId, String text) {
        if (answer.getAuthoritativeTranscript() != null) {
            log.debug("Answer {} already has an authoritative transcript — ignoring duplicate", answer.getId());
            return;
        }
        answer.setAuthoritativeTranscript(text);
        if (answer.getTranscriptId() == null) {
            answer.setTranscriptId(transcriptId);
        }
        Boolean mismatch = computeTranscriptMismatch(answer.getRealtimeTranscript(), text);
        answer.setTranscriptMismatch(mismatch);
        answerRepo.save(answer);
        logTransition(answer.getId(), answer.getStatus(), answer.getStatus(),
                "authoritative-transcript-attached", Map.of(
                        "transcriptId", String.valueOf(transcriptId),
                        "mismatch", String.valueOf(mismatch)));
        log.info("Answer {} ({}) attached authoritative batch transcript — mismatch={}",
                answer.getId(), answer.getStatus(), mismatch);
    }

    /**
     * Cheap divergence check between the real-time transcript that scored an
     * answer and the batch transcript that arrived later. Flags a material
     * mismatch on either a big length difference or low word overlap. Returns
     * {@code null} when there's nothing to compare (legacy answer / blank text).
     */
    private static Boolean computeTranscriptMismatch(String realtime, String batch) {
        if (realtime == null || realtime.isBlank() || batch == null || batch.isBlank()) {
            return null;
        }
        String[] a = realtime.toLowerCase().trim().split("\\s+");
        String[] b = batch.toLowerCase().trim().split("\\s+");
        if (a.length == 0 || b.length == 0) {
            return null;
        }
        double lenRatio = (double) Math.min(a.length, b.length) / Math.max(a.length, b.length);
        java.util.Set<String> sa = new java.util.HashSet<>(java.util.Arrays.asList(a));
        java.util.Set<String> sb = new java.util.HashSet<>(java.util.Arrays.asList(b));
        java.util.Set<String> inter = new java.util.HashSet<>(sa);
        inter.retainAll(sb);
        java.util.Set<String> union = new java.util.HashSet<>(sa);
        union.addAll(sb);
        double jaccard = union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
        return lenRatio < 0.6 || jaccard < 0.5;
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

        // End-to-end wall-clock for the video/behavioral path: from the moment
        // the candidate hit submit (answer row created) to the moment the score
        // lands. Spans STT + AI evaluation + all Kafka/outbox hops. Grep
        // "TIMING e2e" on the VPS alongside the "TIMING stt"/"TIMING ai_*" lines
        // to attribute where the wait is spent.
        if (answer.getSubmittedAt() != null) {
            log.info("TIMING e2e answerId={} type={} submitToScoredMs={}",
                    answerId, sq.getQuestionType(),
                    java.time.Duration.between(
                            answer.getSubmittedAt().toInstant(),
                            OffsetDateTime.now().toInstant()).toMillis());
        }

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
        // Run the planner as a best-effort step. The answer's score is already
        // persisted above; a planner/follow-up failure (e.g. ai-service 500 on
        // /follow-up/generate) must NOT roll back that score nor poison the
        // Kafka record — otherwise the answer is stranded in EVALUATING and the
        // session can never be finalised (final_score stays null forever).
        try {
            runPlannerForAnswer(session, sq, verdict);
        } catch (Exception e) {
            log.error("Planner failed for answer {} (session {}) — score already "
                    + "persisted; skipping follow-up/next-question this tick",
                    answerId, session.getId(), e);
        }

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

        // Time-aware fill: hand the planner the live session clock so that when
        // every topic is covered but a full Q&A cycle of time remains, it
        // deepens (re-probes the weakest covered topic) instead of ending early.
        long secondsRemaining = 0;
        if (session.getStartedAt() != null) {
            OffsetDateTime deadline = session.getStartedAt().plusMinutes(session.getTimeBudgetMinutes());
            secondsRemaining = Math.max(0,
                    java.time.Duration.between(OffsetDateTime.now(), deadline).getSeconds());
        }
        // Per-question pacing cap by interview type (product spec: a BEHAVIORAL
        // answer runs ~5 min, a CORE answer ~10 min). This is the deepen
        // time-gate threshold, sized to the cap (worst case) so we never pin a
        // bonus question the candidate can't finish before the deadline. Derived
        // from the type — NOT timeBudget/questionBudget — so CORE (10-min
        // answers, higher budget) is paced correctly rather than over-eagerly.
        int capMinutesPerQuestion = switch (session.getInterviewType()) {
            case CORE   -> 10;
            case CODING -> 10; // planner isn't used for CODING; harmless default
            default     -> 5;  // BEHAVIORAL
        };
        int secondsPerQuestion = capMinutesPerQuestion * 60;

        PlannerInputs inputs = new PlannerInputs(
                session, blueprint, currentState, currentTopicConfig, verdict,
                statuses, (int) pinnedSoFar, (int) secondsRemaining, secondsPerQuestion);
        PlannerOutcome outcome = planner.plan(inputs);

        sideEffectApplier.apply(outcome.sideEffects(), session, currentState);

        // Resolve the decision — might pin a new question or end the session.
        switch (outcome.decision()) {
            case PlannerDecision.AskFollowUp f -> {
                if (!handleFollowUp(session, sq, f, verdict, (int) pinnedSoFar + 1)) {
                    // No follow-up could be produced (AI unavailable / empty).
                    // Don't strand the candidate on a frozen screen — close this
                    // topic and advance to the next one, or end the session.
                    PlannerOutcome fb = planner.closeTopicAndAdvance(inputs);
                    sideEffectApplier.apply(fb.sideEffects(), session, currentState);
                    switch (fb.decision()) {
                        case PlannerDecision.MoveToNextTopic m -> handleMoveNext(session, m, (int) pinnedSoFar + 1);
                        case PlannerDecision.EndSession e -> handleEndSession(session, e.reason());
                        // closeTopicAndAdvance never re-asks a follow-up; end defensively.
                        case PlannerDecision.AskFollowUp ignored ->
                                handleEndSession(session, PlannerDecision.EndSession.EndReason.COVERAGE_COMPLETE);
                    }
                }
            }
            case PlannerDecision.MoveToNextTopic m -> handleMoveNext(session, m, (int) pinnedSoFar + 1);
            case PlannerDecision.EndSession e -> handleEndSession(session, e.reason());
        }
    }

    /**
     * @return {@code true} if a follow-up question was pinned (pre-authored or
     *         AI-generated), {@code false} if AI generation failed — the caller
     *         then advances to the next topic so the interview never stalls.
     */
    private boolean handleFollowUp(
            InterviewSession session, SessionQuestion parent,
            PlannerDecision.AskFollowUp follow, AssessmentVerdict verdict, int sequence) {

        // Tier 1: pre-authored.
        var fromBank = questionPicker.pickFollowUpFromBank(
                session.getId(), parent, follow.weakTarget(), follow.difficulty(), sequence);
        if (fromBank.isPresent()) {
            log.info("Pinned PRE_AUTHORED follow-up for session {} parent {}",
                    session.getId(), parent.getId());
            return true;
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

        try {
            questionPicker.pickFollowUpFromAi(
                    session.getId(), parent, follow.weakTarget(), verdict.strongTargets(),
                    follow.difficulty(),
                    parentText, parentTranscript,
                    (String) snap.get("competency"), (String) snap.get("domain"),
                    extractStringList(snap.get("expectedSignals")),
                    extractStringList(snap.get("keyConcepts")),
                    language, sequence);
        } catch (Exception e) {
            log.warn("AI follow-up generation failed for session {} parent {}: {} — "
                    + "will advance to next topic", session.getId(), parent.getId(), e.getMessage());
            return false;
        }
        log.info("Pinned AI_GENERATED follow-up for session {} parent {}", session.getId(), parent.getId());
        return true;
    }

    private void handleMoveNext(InterviewSession session, PlannerDecision.MoveToNextTopic m, int sequence) {
        UserProfileResponse profile = loadProfile(session);
        List<String> excludeIds = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(session.getId()).stream()
                .map(SessionQuestion::getQuestionId)
                .filter(java.util.Objects::nonNull)
                .toList();

        SessionQuestion next;
        try {
            next = questionPicker.pickForTopic(
                    session.getId(), m.nextTopic(), m.openingDifficulty(), profile, excludeIds, sequence);
        } catch (BusinessException e) {
            // No bank question left for this topic (every candidate already
            // excluded, even after relaxing). Common on the deepen branch, which
            // re-probes the weakest covered topic and can drain that competency's
            // pool. End the session gracefully instead of erroring the answer
            // flow — the overall review still runs on what was answered.
            // QuestionPicker is not @Transactional, so catching here does not
            // mark the surrounding transaction rollback-only.
            log.info("No bank question to advance session {} (topic {}): {} — ending session",
                    session.getId(), m.nextTopic().getTopicValue(), e.getMessage());
            handleEndSession(session, PlannerDecision.EndSession.EndReason.COVERAGE_COMPLETE);
            return;
        }

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

    /** Single-line, length-capped transcript for logs (avoid flooding on long answers). */
    private static String transcriptPreview(String text) {
        if (text == null) return "";
        String oneLine = text.replaceAll("\\s+", " ").trim();
        int cap = 2000;
        return oneLine.length() <= cap ? oneLine : oneLine.substring(0, cap) + "…(+" + (oneLine.length() - cap) + " chars)";
    }

    /**
     * Parent transcript for AI follow-up context. Reads the stored transcript
     * columns first (realtime-stt-plan.md §12.1): the fast path's
     * {@code realtimeTranscript} is the text that actually scored the parent,
     * and the legacy path stores the batch text as {@code authoritativeTranscript}
     * — so a follow-up never depends on the AI echoing {@code answer.transcript}
     * back in {@code rawEvaluation}. The rawEvaluation read stays as a fallback
     * for rows written before these columns existed.
     */
    private static String extractTranscript(Answer parentAnswer) {
        if (parentAnswer == null) return "";
        if (parentAnswer.getRealtimeTranscript() != null && !parentAnswer.getRealtimeTranscript().isBlank()) {
            return parentAnswer.getRealtimeTranscript();
        }
        if (parentAnswer.getAuthoritativeTranscript() != null
                && !parentAnswer.getAuthoritativeTranscript().isBlank()) {
            return parentAnswer.getAuthoritativeTranscript();
        }
        if (parentAnswer.getRawEvaluation() == null) return "";
        Object answer = parentAnswer.getRawEvaluation().get("answer");
        if (answer instanceof Map<?, ?> m) {
            Object t = m.get("transcript");
            if (t instanceof String s) return s;
        }
        return "";
    }
}
