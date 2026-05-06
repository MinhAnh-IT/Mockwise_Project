package com.mockwise.interview;

import com.mockwise.interview.client.ai.AiServiceAdapter;
import com.mockwise.interview.client.questionbank.QuestionBankAdapter;
import com.mockwise.interview.client.questionbank.dto.MarkAskedResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionCandidate;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterResponse;
import com.mockwise.interview.client.storage.StorageAdapter;
import com.mockwise.interview.client.ttsstt.TtsSttAdapter;
import com.mockwise.interview.client.userprofile.UserProfileAdapter;
import com.mockwise.interview.client.userprofile.dto.PositionResponse;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import com.mockwise.interview.dto.request.StartSessionInput;
import com.mockwise.interview.dto.response.StartSessionOutput;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.entity.SessionTopicState;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.TopicKind;
import com.mockwise.interview.enums.TopicStatus;
import com.mockwise.interview.repository.AnswerRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import com.mockwise.interview.repository.SessionQuestionRepository;
import com.mockwise.interview.repository.SessionTopicStateRepository;
import com.mockwise.interview.service.AnswerService;
import com.mockwise.interview.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end happy-path coverage for the orchestrator. Boots the full
 * Spring context against the VPS {@code mockwise_interview} DB (V1 + V2
 * already applied), with all five Feign adapters replaced by Mockito
 * stubs. Each test runs inside a transaction that rolls back at the end
 * so the shared DB doesn't accumulate test data — the V2 seed
 * blueprints stay intact because they live in a different transaction
 * (committed at migration time).
 *
 * <p>Two scenarios — together they exercise every layer except the
 * Kafka serialization (covered by JpaSchemaValidationIT boot smoke):
 *
 * <ol>
 *   <li>{@link #start_pullsProfile_loadsBlueprint_pinsFirstQuestion} —
 *       calls {@code SessionService.start} and checks the session row,
 *       seeded topic-state matrix, and pinned first question all land.</li>
 *   <li>{@link #applyEvaluationCompleted_strongAnswer_closesTopicAndPinsNext}
 *       — fakes a STRONG behavioral verdict landing for the first
 *       question, then asserts the planner closed the topic STRONG,
 *       picked the next blueprint topic, and pinned a new
 *       session_question with the right metadata.</li>
 * </ol>
 *
 * <p>Anything more (Case A1/A2/B/C subtleties, end-of-session
 * termination, stretch-mode trigger) is already covered by the 14
 * NextQuestionPlannerTest cases at the unit-test layer.
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class InterviewFlowIT {

    private static final String USER_ID = "it-test-user";

    @MockBean
    UserProfileAdapter userProfileAdapter;
    @MockBean QuestionBankAdapter questionBankAdapter;
    @MockBean StorageAdapter storageAdapter;
    @MockBean TtsSttAdapter ttsSttAdapter;
    @MockBean AiServiceAdapter aiServiceAdapter;

    @Autowired SessionService sessionService;
    @Autowired AnswerService answerService;
    @Autowired AnswerRepository answerRepo;
    @Autowired InterviewSessionRepository sessionRepo;
    @Autowired SessionTopicStateRepository topicStateRepo;
    @Autowired SessionQuestionRepository sessionQuestionRepo;

    // ── Test 1: /start ───────────────────────────────────────────────────────

    @Test
    void start_pullsProfile_loadsBlueprint_pinsFirstQuestion() {
        // Profile points at the BACKEND-mid-MIXED blueprint seeded in V2.
        when(userProfileAdapter.getProfile(USER_ID)).thenReturn(profile("Backend", "Mid", 3));

        // Question-bank returns one BEHAVIORAL candidate matching the first
        // blueprint topic (OWNERSHIP, EASY, opener).
        when(questionBankAdapter.filter(any())).thenReturn(filterResponse(List.of(
                candidate("q-bank-001", QuestionType.BEHAVIORAL, Difficulty.EASY,
                        "Tell me about a time you took ownership.",
                        "OWNERSHIP", null, true, 0L)
        )));
        when(questionBankAdapter.markAskedSoft(anyString()))
                .thenReturn(Optional.of(new MarkAskedResponse("q-bank-001", 1L, OffsetDateTime.now())));

        StartSessionOutput out = sessionService.start(USER_ID,
                new StartSessionInput(InterviewType.MIXED, null));

        // Session metadata
        assertThat(out.sessionId()).isNotNull();
        assertThat(out.targetRole()).isEqualTo("BACKEND");
        assertThat(out.level()).isEqualTo("mid");
        assertThat(out.questionBudget()).isEqualTo(8);
        assertThat(out.timeBudgetMinutes()).isEqualTo(45);

        // First question pinned
        assertThat(out.firstQuestion().questionId()).isEqualTo("q-bank-001");
        assertThat(out.firstQuestion().questionType()).isEqualTo(QuestionType.BEHAVIORAL);
        assertThat(out.firstQuestion().sequence()).isEqualTo(1);
        assertThat(out.firstQuestion().topicKind()).isEqualTo(TopicKind.COMPETENCY);
        assertThat(out.firstQuestion().topicValue()).isEqualTo("OWNERSHIP");
        assertThat(out.firstQuestion().difficulty()).isEqualTo(Difficulty.EASY);

        // Session row persisted
        var session = sessionRepo.findById(out.sessionId()).orElseThrow();
        assertThat(session.getUserId()).isEqualTo(USER_ID);
        assertThat(session.getInterviewType()).isEqualTo(InterviewType.MIXED);

        // Topic-state matrix seeded — all 6 topics from BACKEND-mid-MIXED.
        // The first topic (OWNERSHIP) is now PROBING, the rest NOT_TESTED.
        List<SessionTopicState> states = topicStateRepo.findByIdSessionId(out.sessionId());
        assertThat(states).hasSize(6);
        var ownershipState = states.stream()
                .filter(s -> "OWNERSHIP".equals(s.getId().getTopicValue()))
                .findFirst().orElseThrow();
        assertThat(ownershipState.getStatus()).isEqualTo(TopicStatus.PROBING);
        assertThat(ownershipState.getQuestionsAsked()).isEqualTo(1);
        long stillFresh = states.stream().filter(s -> s.getStatus() == TopicStatus.NOT_TESTED).count();
        assertThat(stillFresh).isEqualTo(5);

        // session_question row written for sequence=1
        List<SessionQuestion> qs = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(out.sessionId());
        assertThat(qs).hasSize(1);
        assertThat(qs.get(0).getQuestionId()).isEqualTo("q-bank-001");
    }

    // ── Test 2: applyEvaluationCompleted ─────────────────────────────────────

    @Test
    void applyEvaluationCompleted_strongAnswer_closesTopicAndPinsNext() {
        // Re-use the start flow from test 1 to put us in a "session in
        // progress, first question pinned" state. Stubs from test 1's
        // setup are not shared — re-stub here.
        when(userProfileAdapter.getProfile(USER_ID)).thenReturn(profile("Backend", "Mid", 3));
        when(questionBankAdapter.filter(any())).thenReturn(filterResponse(List.of(
                candidate("q-bank-001", QuestionType.BEHAVIORAL, Difficulty.EASY,
                        "Tell me about a time you took ownership.",
                        "OWNERSHIP", null, true, 0L)
        )));
        when(questionBankAdapter.markAskedSoft(anyString()))
                .thenReturn(Optional.of(new MarkAskedResponse("q-bank-001", 1L, OffsetDateTime.now())));

        StartSessionOutput started = sessionService.start(USER_ID,
                new StartSessionInput(InterviewType.MIXED, null));
        UUID sessionId = started.sessionId();
        UUID firstSqId = started.firstQuestion().sessionQuestionId();

        // Insert an Answer in EVALUATING state directly — skips the submit
        // + tts-stt dance, which is exercised by JpaSchemaValidationIT
        // boot smoke + the unit tests.
        Answer answer = answerRepo.save(Answer.builder()
                .sessionId(sessionId)
                .sessionQuestionId(firstSqId)
                .type(AnswerType.VIDEO)
                .status(AnswerStatus.EVALUATING)
                .submittedAt(OffsetDateTime.now())
                .build());

        // After the planner moves on it pins the next topic — point the
        // mock filter at a different candidate for the second call.
        when(questionBankAdapter.filter(any())).thenReturn(filterResponse(List.of(
                candidate("q-bank-002", QuestionType.BEHAVIORAL, Difficulty.MEDIUM,
                        "Tell me about a time you handled a disagreement.",
                        "CONFLICT_RESOLUTION", null, false, 0L)
        )));
        when(questionBankAdapter.markAskedSoft("q-bank-002"))
                .thenReturn(Optional.of(new MarkAskedResponse("q-bank-002", 1L, OffsetDateTime.now())));

        // Apply a STRONG behavioral verdict.
        answerService.applyEvaluationCompleted(answer.getId(), strongBehavioralPayload(sessionId));

        // ── Assertions ────────────────────────────────────────────────────────

        // Answer flipped to SCORED with a real score.
        Answer reloaded = answerRepo.findById(answer.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnswerStatus.SCORED);
        assertThat(reloaded.getScore()).isGreaterThanOrEqualTo(8.0f);
        assertThat(reloaded.getScoredAt()).isNotNull();
        assertThat(reloaded.getVerdict()).isNotEmpty();

        // OWNERSHIP topic closed STRONG.
        var ownership = topicStateRepo.findByIdSessionId(sessionId).stream()
                .filter(s -> "OWNERSHIP".equals(s.getId().getTopicValue()))
                .findFirst().orElseThrow();
        assertThat(ownership.getStatus()).isEqualTo(TopicStatus.STRONG);
        assertThat(ownership.getClosedAt()).isNotNull();
        assertThat(ownership.getLastScore()).isGreaterThanOrEqualTo(8.0f);

        // Session.runningStrongCount bumped by 1.
        var session = sessionRepo.findById(sessionId).orElseThrow();
        assertThat(session.getRunningStrongCount()).isEqualTo(1);

        // The planner picked the next blueprint topic (importance DESC,
        // order_hint ASC, prefer different kind from current). After
        // OWNERSHIP closes, the next-best is CONFLICT_RESOLUTION (HIGH,
        // order=2, COMPETENCY same as current — but no DOMAIN with
        // higher importance is available besides DATABASE which has the
        // same importance and a higher order_hint). So the planner
        // moves to CONFLICT_RESOLUTION.
        var pinned = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(sessionId);
        assertThat(pinned).hasSize(2);
        var second = pinned.get(1);
        assertThat(second.getSequence()).isEqualTo(2);
        assertThat(second.getQuestionId()).isEqualTo("q-bank-002");
        assertThat(second.getTopicValue()).isEqualTo("CONFLICT_RESOLUTION");
    }

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private static UserProfileResponse profile(String trackName, String levelName, int experience) {
        return new UserProfileResponse(
                USER_ID,
                "IT Tester",
                new PositionResponse("pos", "track-id", trackName, "level-id", levelName),
                "Hanoi",
                experience,
                null, null,
                List.of("java", "spring", "postgresql"),
                "vi",
                experience,
                List.of("fintech")
        );
    }

    private static QuestionFilterResponse filterResponse(List<QuestionCandidate> candidates) {
        return new QuestionFilterResponse(candidates, candidates.size());
    }

    private static QuestionCandidate candidate(
            String id, QuestionType type, Difficulty difficulty, String text,
            String competency, String domain, boolean isOpener, long askCount) {
        return new QuestionCandidate(
                id, type, difficulty, text, competency, domain,
                List.of("opener", "story"), null, isOpener, askCount);
    }

    /**
     * Mirror of the AI service's {@code BehavioralOutput} JSON shape
     * (camelCase keys, matching CamelModel emit) — passed through
     * Jackson + the mapper inside AnswerService.applyEvaluationCompleted
     * exactly as the evaluation-completed Kafka consumer would.
     */
    private static Map<String, Object> strongBehavioralPayload(UUID sessionId) {
        return Map.of(
                "sessionId", sessionId.toString(),
                "interviewType", "behavioral",
                "overallScore", 88,
                "completeness", "COMPLETE",
                "scores", Map.of(
                        "starStructure",  Map.of("score", 90, "max", 100, "weight", 0.25, "note", ""),
                        "relevance",      Map.of("score", 85, "max", 100, "weight", 0.20, "note", ""),
                        "specificity",    Map.of("score", 88, "max", 100, "weight", 0.25, "note", ""),
                        "impactResult",   Map.of("score", 88, "max", 100, "weight", 0.20, "note", ""),
                        "selfAwareness",  Map.of("score", 85, "max", 100, "weight", 0.10, "note", "")
                ),
                "signalCoverage", List.of(
                        Map.of("signalName", "specific_recent_example", "detected", true),
                        Map.of("signalName", "names_own_role_clearly",  "detected", true),
                        Map.of("signalName", "fact_based_argument",     "detected", true),
                        Map.of("signalName", "clear_resolution",        "detected", true)
                ),
                "redFlags", List.of(),
                "summary", Map.of(
                        "grade", "B",
                        "hireSignal", "yes",
                        "oneLineVerdict", "Strong delivery."
                )
        );
    }

}
