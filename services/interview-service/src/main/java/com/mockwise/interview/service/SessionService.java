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
import com.mockwise.interview.dto.response.PinnedQuestionView;
import com.mockwise.interview.dto.response.SessionView;
import com.mockwise.interview.dto.request.StartSessionInput;
import com.mockwise.interview.dto.response.StartSessionOutput;
import com.mockwise.interview.common.util.BlueprintNormalizer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    BlueprintLoader blueprintLoader;
    QuestionPicker questionPicker;
    InterviewSessionRepository sessionRepo;
    SessionTopicStateRepository topicStateRepo;
    SessionQuestionRepository sessionQuestionRepo;
    AnswerRepository answerRepo;

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
                .globalDifficultyOffset(globalOffset)
                .startedAt(OffsetDateTime.now())
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
                input.timeBudgetMinutesOverride() != null
                        ? input.timeBudgetMinutesOverride()
                        : blueprint.getTimeBudgetMinutes(),
                PinnedQuestionView.fromEntity(firstQuestion));
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

        return new SessionView(
                session.getId(),
                session.getUserId(),
                session.getTargetRole(),
                session.getLevel(),
                session.getInterviewType(),
                session.getStatus(),
                session.getQuestionCount(),
                session.getFinalScore(),
                session.getStartedAt(),
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
                questions.stream().map(PinnedQuestionView::fromEntity).toList()
        );
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
