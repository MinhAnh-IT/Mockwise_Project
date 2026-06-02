package com.mockwise.interview.service;

import com.mockwise.interview.client.userprofile.UserProfileAdapter;
import com.mockwise.interview.client.userprofile.dto.PositionResponse;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.entity.SessionTopicStateId;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.enums.TopicStatus;
import com.mockwise.interview.repository.AnswerRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import com.mockwise.interview.repository.SessionQuestionRepository;
import com.mockwise.interview.repository.SessionTopicStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.dto.response.AnswerView;
import com.mockwise.interview.dto.response.OverallReviewView;
import com.mockwise.interview.dto.response.PinnedQuestionView;
import com.mockwise.interview.dto.response.SessionSummaryView;
import com.mockwise.interview.dto.response.SessionView;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.dto.request.StartSessionInput;
import com.mockwise.interview.dto.response.StartSessionOutput;
import com.mockwise.interview.common.util.BlueprintNormalizer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns session lifecycle: /start, /get, /finish.
 *
 * <p>The /start flow follows question-selection-design.md §2.0 and §3:
 * <ol>
 *   <li>Load profile from user-profile-service.</li>
 *   <li>Map track / level / experience to a blueprint key + global
 *       difficulty offset.</li>
 *   <li>Create the session row, seed session_topic_state[] from the
 *       blueprint.</li>
 *   <li>Pick the first topic (deterministic blueprint sort), call
 *       question-bank /filter, persist as session_question[1].</li>
 * </ol>
 *
 * <p>All four steps run in a single transaction so a failure mid-way
 * doesn't leave a half-built session lying around.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SessionService {

    /** Years of experience expected at each level — used by the cross-check. */
    private static final Map<String, Integer> EXPECTED_MIN_YEARS = Map.of(
            "junior", 0,
            "mid",    2,
            "senior", 5
    );

    UserProfileAdapter userProfileAdapter;
    com.mockwise.interview.client.storage.StorageAdapter storageAdapter;
    BlueprintLoader blueprintLoader;
    QuestionPicker questionPicker;
    InterviewSessionRepository sessionRepo;
    SessionTopicStateRepository topicStateRepo;
    SessionQuestionRepository sessionQuestionRepo;
    AnswerRepository answerRepo;
    SessionFinalizerService sessionFinalizer;
    OutboxWriter outboxWriter;
    ObjectMapper objectMapper;

    // ── /start ───────────────────────────────────────────────────────────────

    @Transactional
    public StartSessionOutput start(String userId, StartSessionInput input) {
        // Bean Validation on the controller's @Valid annotation already
        // enforces non-null interviewType before we get here; this is a
        // defensive belt-and-brace check for service-to-service callers
        // that bypass the controller.
        if (input == null || input.interviewType() == null) {
            throw new BusinessException(StatusCode.VALIDATION_ERROR, "interviewType is required");
        }

        UserProfileResponse profile = userProfileAdapter.getProfile(userId);
        String role  = BlueprintNormalizer.normalizeRole(safeTrack(profile));
        String level = BlueprintNormalizer.normalizeLevel(safeLevel(profile));

        InterviewBlueprint blueprint = blueprintLoader.findFor(role, level, input.interviewType())
                .orElseThrow(() -> new BusinessException(StatusCode.BLUEPRINT_NOT_FOUND));

        int globalOffset = computeDifficultyOffset(profile, level);

        int timeBudget = input.timeBudgetMinutesOverride() != null
                ? input.timeBudgetMinutesOverride()
                : blueprint.getTimeBudgetMinutes();

        // Snapshot the profile onto the session so the answer pipeline
        // (transcript-ready language hint, follow-up + next-topic picks)
        // reads it from here instead of re-fetching user-profile-service
        // ~3× per answer. Profile is effectively static over a session.
        Map<String, Object> meta = new HashMap<>();
        meta.put("profileSnapshot",
                objectMapper.convertValue(profile,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        meta.put("profileSnapshotAt", OffsetDateTime.now().toString());

        // CODING is non-adaptive: pick the whole ordered problem list now
        // (random by the blueprint difficulty plan, no dup), freeze it onto
        // session.metadata, and pin only question 1 — the rest are
        // materialised one-at-a-time on each submit. No topic-state matrix,
        // no planner, no follow-ups.
        if (input.interviewType() == InterviewType.CODING) {
            List<Difficulty> difficultyPlan = blueprintLoader.codingDifficultyPlan(blueprint);
            List<Map<String, Object>> codingPlan = questionPicker.selectCodingPlan(difficultyPlan);
            meta.put("codingPlan", codingPlan);

            InterviewSession session = sessionRepo.save(InterviewSession.builder()
                    .userId(userId)
                    .blueprintId(blueprint.getId())
                    .targetRole(role)
                    .level(level)
                    .interviewType(input.interviewType())
                    .status(SessionStatus.IN_PROGRESS)
                    .questionCount(codingPlan.size())
                    .timeBudgetMinutes(timeBudget)
                    .globalDifficultyOffset(globalOffset)
                    .startedAt(OffsetDateTime.now())
                    .metadata(meta)
                    .build());

            SessionQuestion firstQuestion =
                    questionPicker.pinCoding(session.getId(), codingPlan.get(0), 1);

            return new StartSessionOutput(
                    session.getId(),
                    role,
                    level,
                    input.interviewType(),
                    codingPlan.size(),
                    timeBudget,
                    session.getStartedAt(),
                    deadlineOf(session),
                    signAudio(PinnedQuestionView.fromEntity(firstQuestion)).redacted());
        }

        InterviewSession session = sessionRepo.save(InterviewSession.builder()
                .userId(userId)
                .blueprintId(blueprint.getId())
                .targetRole(role)
                .level(level)
                .interviewType(input.interviewType())
                // Skip CREATED — the user is already engaged. Per §3 of the
                // selection doc the session is in flight from the very first
                // question, and a separate CREATED tick adds no value.
                .status(SessionStatus.IN_PROGRESS)
                .questionCount(blueprint.getQuestionBudget())
                .timeBudgetMinutes(timeBudget)
                .globalDifficultyOffset(globalOffset)
                .startedAt(OffsetDateTime.now())
                .metadata(meta)
                .build());

        blueprintLoader.seedTopicStates(session.getId(), blueprint);

        BlueprintTopic firstTopic = blueprintLoader.pickFirstTopic(blueprint);
        Difficulty openingDifficulty = computeOpeningDifficulty(firstTopic, globalOffset);

        SessionQuestion firstQuestion = questionPicker.pickForTopic(
                session.getId(), firstTopic, openingDifficulty, profile, List.of(), /*sequence*/ 1);

        // Promote the topic to PROBING + record the asked count. Done after
        // pickForTopic so a question-bank failure rolls back via the @Transactional.
        SessionTopicState firstTopicState = topicStateRepo.findById(
                new SessionTopicStateId(session.getId(), firstTopic.getKind(), firstTopic.getTopicValue()))
                .orElseThrow();
        firstTopicState.setStatus(TopicStatus.PROBING);
        firstTopicState.setQuestionsAsked(1);
        firstTopicState.setLastDifficulty(openingDifficulty);
        topicStateRepo.save(firstTopicState);

        return new StartSessionOutput(
                session.getId(),
                role,
                level,
                input.interviewType(),
                blueprint.getQuestionBudget(),
                timeBudget,
                session.getStartedAt(),
                deadlineOf(session),
                // Session is IN_PROGRESS — strip rubric/classification fields
                // so the candidate doesn't see expectedPoints, difficulty, topic, etc.
                signAudio(PinnedQuestionView.fromEntity(firstQuestion)).redacted());
    }

    // ── /list (history) ──────────────────────────────────────────────────────

    /**
     * Returns the caller's sessions newest-first as a slim summary list.
     * Used by the FE history page; full topic / question / overall-review
     * detail is loaded via {@link #getForUser} on click.
     */
    @Transactional(readOnly = true)
    public Page<SessionSummaryView> listForUser(String userId, Pageable pageable) {
        return sessionRepo.findByUserId(userId, pageable)
                .map(s -> SessionSummaryView.fromEntity(s, objectMapper));
    }

    // ── /get ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SessionView getForUser(UUID sessionId, String userId) {
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(StatusCode.SESSION_NOT_OWNER);
        }

        List<SessionTopicState> states = topicStateRepo.findByIdSessionId(sessionId);
        List<SessionQuestion> questions = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(sessionId);

        // Only the SCORED report view is allowed to expose rubric and
        // classification fields (expectedPoints, difficulty, topic, source...).
        // Mid-flight polls strip them so a candidate hitting reload can't read
        // the answer key or topic distribution off the API.
        boolean revealFull = session.getStatus() == SessionStatus.SCORED;

        // Look up latest-answer per question only when revealing — the
        // candidate doesn't see their own past answers mid-flight, so
        // we'd just throw the data away for redacted views. Embed the
        // signed video URL + verdict directly so the FE can render the
        // per-question card without a follow-up call to /answers/{aid}.
        Map<UUID, com.mockwise.interview.enums.QuestionType> typeBySq = questions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SessionQuestion::getId, SessionQuestion::getQuestionType));
        Map<UUID, AnswerView> answerBySq = revealFull
                ? loadAnswersBySessionQuestion(sessionId, typeBySq, userId)
                : Map.of();

        // Which pinned questions already have an answer — exposed mid-flight as
        // a non-sensitive boolean so the FE can resume into the right phase on
        // reload (answered + waiting vs not-yet-answered + recording). Cheap id
        // load; on the SCORED path we already have the full answer map.
        java.util.Set<UUID> answeredSqIds = revealFull
                ? answerBySq.keySet()
                : answerRepo.findBySessionId(sessionId).stream()
                        .map(Answer::getSessionQuestionId)
                        .collect(java.util.stream.Collectors.toSet());

        return new SessionView(
                session.getId(),
                session.getUserId(),
                session.getTargetRole(),
                session.getLevel(),
                session.getInterviewType(),
                session.getStatus(),
                session.getQuestionCount(),
                session.getTimeBudgetMinutes(),
                session.getFinalScore(),
                session.getStartedAt(),
                deadlineOf(session),
                session.getFinishedAt(),
                session.getScoredAt(),
                states.stream()
                        .map(s -> new SessionView.TopicProgress(
                                s.getId().getTopicKind().name(),
                                s.getId().getTopicValue(),
                                s.getStatus(),
                                s.getQuestionsAsked(),
                                s.getFollowUpsUsed(),
                                s.getLastScore()))
                        .toList(),
                questions.stream()
                        .map(PinnedQuestionView::fromEntity)
                        .map(this::signAudio)
                        .map(v -> {
                            AnswerView a = answerBySq.get(v.sessionQuestionId());
                            return a == null
                                    ? v
                                    : v.withLatestAnswerId(a.answerId()).withAnswer(a);
                        })
                        .map(v -> v.withAnswered(answeredSqIds.contains(v.sessionQuestionId())))
                        .map(v -> revealFull ? v : v.redacted())
                        .toList(),
                extractOverallReview(session)
        );
    }

    /**
     * Loads every answer pinned to the session in one query and maps each by
     * its parent {@code session_question.id}. Builds the {@link AnswerView}
     * with results revealed (caller already gated on SCORED) and a signed,
     * same-origin video URL so the FE can drop straight into a {@code <video src>}.
     *
     * <p>The schema enforces one answer per session_question (see
     * {@link AnswerRepository#findBySessionQuestionId} returning Optional) —
     * if a duplicate sneaks in we keep the last one written, which matches
     * how the planner overwrites prior verdicts on replay.
     */
    private Map<UUID, AnswerView> loadAnswersBySessionQuestion(
            UUID sessionId,
            Map<UUID, com.mockwise.interview.enums.QuestionType> questionTypeBySessionQuestionId,
            String requesterUserId) {
        List<Answer> answers = answerRepo.findBySessionId(sessionId);
        Map<UUID, AnswerView> out = new HashMap<>(answers.size());
        for (Answer a : answers) {
            com.mockwise.interview.enums.QuestionType qType =
                    questionTypeBySessionQuestionId.get(a.getSessionQuestionId());
            AnswerView view = AnswerView.fromEntity(a, qType, /* revealResults */ true, objectMapper)
                    .withSignedMedia(id -> storageAdapter
                            .signInterviewVideoUrl(id, requesterUserId).orElse(null));
            out.put(a.getSessionQuestionId(), view);
        }
        return out;
    }

    /**
     * Returns the AI overall_reviewer output stashed in
     * {@code metadata.overallReview} once the session reaches SCORED, mapped
     * to the typed {@link OverallReviewView}. Null for any other status, or
     * when the metadata blob is missing / malformed — keeps mid-session polls
     * from leaking the result and stops a corrupt row from failing the call.
     */
    @SuppressWarnings("unchecked")
    private OverallReviewView extractOverallReview(InterviewSession session) {
        if (session.getStatus() != SessionStatus.SCORED || session.getMetadata() == null) {
            return null;
        }
        Object review = session.getMetadata().get("overallReview");
        if (!(review instanceof Map<?, ?> map)) return null;
        try {
            return objectMapper.convertValue((Map<String, Object>) map, OverallReviewView.class);
        } catch (IllegalArgumentException ex) {
            log.warn("Session {} has malformed overallReview metadata — returning null", session.getId(), ex);
            return null;
        }
    }

    /**
     * Resolves the question's {@code audioKey} into a short-lived
     * presigned GET URL the FE can stream. Soft-fails — if storage is
     * down we still return the view, just without the URL filled in.
     */
    private PinnedQuestionView signAudio(PinnedQuestionView view) {
        return view.withSignedAudio(
                key -> storageAdapter.signQuestionAudioUrl(key).orElse(null));
    }

    // ── Apply overall review (Kafka consumer entry point) ────────────────────

    /**
     * Called by {@code SessionEvaluationConsumer.onCompleted}. Lands the
     * AI {@code overall_reviewer} output on the session row and flips
     * status to SCORED. Idempotent — duplicate deliveries are a no-op.
     *
     * <p>Concurrent calls are serialised via the row-level lock; a second
     * delivery that arrives after the first has committed reads
     * {@code status == SCORED} and bails before mutating anything.
     */
    @Transactional
    public void applyOverallReview(UUID sessionId, Map<String, Object> result) {
        InterviewSession s = sessionRepo.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (s.getStatus() == SessionStatus.SCORED) {
            log.debug("Session {} already SCORED — ignoring duplicate overall-review", sessionId);
            return;
        }

        Float overallScore = parseFloat(result.get("overallScore"));
        s.setFinalScore(overallScore);

        Map<String, Object> meta = s.getMetadata() != null ? s.getMetadata() : new HashMap<>();
        meta.put("overallReview", result);
        meta.remove("overallReviewError");
        s.setMetadata(meta);

        s.setStatus(SessionStatus.SCORED);
        s.setScoredAt(OffsetDateTime.now());
        sessionRepo.save(s);

        log.info("Session {} → SCORED finalScore={}", sessionId, overallScore);

        // Stage interview-scored for mail-service.
        Map<String, Object> mailPayload = new HashMap<>();
        mailPayload.put("sessionId", s.getId().toString());
        mailPayload.put("userId", s.getUserId());
        mailPayload.put("finalScore", overallScore);
        mailPayload.put("grade", result.get("grade"));
        mailPayload.put("hireSignal", result.get("hireSignal"));
        mailPayload.put("scoredAt", s.getScoredAt().toString());
        outboxWriter.stage(
                com.mockwise.interview.message.constants.KafkaTopics.INTERVIEW_SCORED,
                "INTERVIEW_SCORED", s.getId(), mailPayload);
    }

    /**
     * Called by {@code SessionEvaluationConsumer.onFailed}. Records the
     * error on session metadata but leaves status at COMPLETED so an
     * operator can clear {@code metadata.sessionEvalRequestedAt} and
     * replay through {@link SessionFinalizerService#maybeRequestOverallReview}.
     */
    @Transactional
    public void applyOverallReviewFailed(UUID sessionId, String error, String detail) {
        InterviewSession s = sessionRepo.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (s.getStatus() == SessionStatus.SCORED) {
            log.debug("Session {} already SCORED — ignoring late overall-review-failed", sessionId);
            return;
        }
        Map<String, Object> meta = s.getMetadata() != null ? s.getMetadata() : new HashMap<>();
        Map<String, Object> err = new HashMap<>();
        err.put("error", error);
        err.put("detail", detail);
        err.put("occurredAt", OffsetDateTime.now().toString());
        meta.put("overallReviewError", err);
        s.setMetadata(meta);
        sessionRepo.save(s);
        log.warn("Session {} overall-review failed: {} ({})", sessionId, error, detail);
    }

    private static Float parseFloat(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    // ── /finish ──────────────────────────────────────────────────────────────

    /**
     * User-triggered hard stop. Flips status → COMPLETED. The downstream
     * SCORED transition is a separate event (background) once every
     * answer hits SCORED / FAILED.
     */
    @Transactional
    public void finish(UUID sessionId, String userId) {
        InterviewSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(StatusCode.SESSION_NOT_OWNER);
        }
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            session.setStatus(SessionStatus.COMPLETED);
            session.setFinishedAt(OffsetDateTime.now());
            sessionRepo.save(session);
            log.info("Session {} → COMPLETED (user-stopped)", sessionId);
        }
        // Already COMPLETED / SCORED / CANCELLED → idempotent no-op.
        // Try the gate every time — when the user clicks /finish before the
        // last answer's evaluation lands, this call is a no-op and the
        // gate fires later when the evaluation-completed consumer runs.
        sessionFinalizer.maybeRequestOverallReview(sessionId);
    }

    // ── Session time budget ──────────────────────────────────────────────────

    /**
     * Wall-clock end of the interview = {@code startedAt + timeBudgetMinutes}.
     * The interview is bounded by this single session-level clock; there is
     * no per-question time limit. Null only if the session never started.
     */
    public static OffsetDateTime deadlineOf(InterviewSession s) {
        return s.getStartedAt() == null
                ? null
                : s.getStartedAt().plusMinutes(s.getTimeBudgetMinutes());
    }

    /**
     * If the session is IN_PROGRESS and now past its deadline, finalize it
     * exactly like {@link #finish} — flip → COMPLETED and gate the overall
     * review. No ownership check: this is the shared end-of-clock path used
     * by the in-flight guard ({@link #ensureWithinTimeBudget}) and the
     * {@link com.mockwise.interview.service.SessionDeadlineReaper}.
     *
     * <p>Caller must already hold a transaction (the session row is mutated
     * and saved here).
     *
     * @return true iff the clock is up (this call ended it, or it was
     *         already ended by time on a prior call within the deadline race)
     */
    boolean applyTimeBudgetExpiry(InterviewSession session) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            // User-finished / cancelled / scored is not a time-up condition —
            // those flow through the normal SESSION_NOT_IN_PROGRESS path.
            return false;
        }
        OffsetDateTime deadline = deadlineOf(session);
        if (deadline == null || !OffsetDateTime.now().isAfter(deadline)) {
            return false;
        }
        session.setStatus(SessionStatus.COMPLETED);
        session.setFinishedAt(OffsetDateTime.now());
        sessionRepo.save(session);
        log.info("Session {} → COMPLETED (time budget {}m exhausted)",
                session.getId(), session.getTimeBudgetMinutes());
        // Same gate as /finish — once every answer is terminal this stages
        // the single overall-review request; a no-op if answers are still
        // processing (the evaluation-completed consumer fires it later).
        sessionFinalizer.maybeRequestOverallReview(session.getId());
        return true;
    }

    /**
     * In-flight guard for write actions (answer submit). Finalizes the
     * session and raises {@code SESSION_TIME_UP} when the clock is up so a
     * late submission can't slip in after the interview has ended. Must run
     * inside the caller's transaction.
     */
    public void ensureWithinTimeBudget(InterviewSession session) {
        if (applyTimeBudgetExpiry(session)) {
            throw new BusinessException(StatusCode.SESSION_TIME_UP);
        }
    }

    /**
     * Reaper entry point — locks the row, then applies the expiry. Its own
     * transaction so one stuck session doesn't block the sweep of the rest.
     *
     * @return true iff the session was (or just became) ended by time
     */
    @Transactional
    public boolean expireIfOverBudget(UUID sessionId) {
        InterviewSession s = sessionRepo.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(StatusCode.SESSION_NOT_FOUND));
        return applyTimeBudgetExpiry(s);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String safeTrack(UserProfileResponse profile) {
        PositionResponse p = profile != null ? profile.position() : null;
        return p != null ? p.trackName() : null;
    }

    private static String safeLevel(UserProfileResponse profile) {
        PositionResponse p = profile != null ? profile.position() : null;
        return p != null ? p.levelName() : null;
    }

    /**
     * Cross-check experience against expected min years for the level. See
     * question-selection-design.md §2.0 second sub-section. yearsInCurrentRole
     * (when present) wins over the total experience: a senior who just
     * switched track gets eased difficulty, regardless of how long they've
     * been in the industry overall.
     */
    private static int computeDifficultyOffset(UserProfileResponse profile, String level) {
        if (profile == null) return 0;
        Integer experience = profile.experience();
        if (experience == null) return 0;

        Integer yearsInRole = profile.yearsInCurrentRole();
        int effectiveYears = yearsInRole != null
                ? Math.min(experience, yearsInRole + 1)
                : experience;

        Integer minYears = EXPECTED_MIN_YEARS.get(level);
        if (minYears == null) return 0;

        int diff = effectiveYears - minYears;
        if (diff < -1) return -1;   // levelled higher than experience supports
        // Stretch trigger is handled inside the planner (running_strong_count);
        // we don't preemptively raise the offset for "senior+" experience here.
        return 0;
    }

    private static Difficulty computeOpeningDifficulty(BlueprintTopic firstTopic, int globalOffset) {
        // step 4: opening difficulty is at most one notch below target,
        // never above EASY when the floor is already EASY.
        Difficulty preliminary = firstTopic.getTargetDifficulty().downgrade();
        if (globalOffset < 0) return preliminary.downgrade();
        return preliminary;
    }
}
