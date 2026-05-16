package com.mockwise.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.client.ai.AiServiceAdapter;
import com.mockwise.interview.client.questionbank.QuestionBankAdapter;
import com.mockwise.interview.client.questionbank.dto.MarkAskedResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionCandidate;
import com.mockwise.interview.client.questionbank.dto.QuestionFilterResponse;
import com.mockwise.interview.client.questionbank.dto.QuestionSnapshotResponse;
import com.mockwise.interview.client.storage.StorageAdapter;
import com.mockwise.interview.client.ttsstt.TtsSttAdapter;
import com.mockwise.interview.client.userprofile.UserProfileAdapter;
import com.mockwise.interview.client.userprofile.dto.PositionResponse;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import com.mockwise.interview.dto.request.StartSessionInput;
import com.mockwise.interview.dto.response.AnswerView;
import com.mockwise.interview.dto.response.SessionView;
import com.mockwise.interview.dto.response.StartSessionOutput;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.entity.OutboxEvent;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.message.constants.KafkaTopics;
import com.mockwise.interview.repository.AnswerRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import com.mockwise.interview.repository.OutboxEventRepository;
import com.mockwise.interview.repository.SessionQuestionRepository;
import com.mockwise.interview.service.AnswerService;
import com.mockwise.interview.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage for the new finalize flow added by Task C — verifies
 * that once every per-answer evaluation lands, the orchestrator stages
 * {@code SESSION_EVALUATION_REQUESTED} on the outbox, and that applying
 * the AI's overall-review result flips the session to SCORED + stages
 * {@code INTERVIEW_SCORED}.
 *
 * <p>STT is bypassed entirely — the user has already verified that path
 * in production. We jump straight from "answer submitted" to
 * {@code applyEvaluationCompleted} (the entry point the
 * {@code EvaluationConsumer} would call after the AI emits a verdict).
 *
 * <p>Real Kafka is bypassed too: the integration profile points
 * {@code bootstrap-servers} at a no-op port and the @KafkaListener
 * containers don't auto-start. The verification here is on the outbox
 * rows produced inside the same transaction as the business writes —
 * which is the contract the OutboxPoller relies on in production.
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class InterviewFinalizeFlowIT {

    private static final String USER_ID = "it-finalize-user";

    @MockBean UserProfileAdapter userProfileAdapter;
    @MockBean QuestionBankAdapter questionBankAdapter;
    @MockBean StorageAdapter storageAdapter;
    @MockBean TtsSttAdapter ttsSttAdapter;
    @MockBean AiServiceAdapter aiServiceAdapter;

    @Autowired SessionService sessionService;
    @Autowired AnswerService answerService;
    @Autowired AnswerRepository answerRepo;
    @Autowired InterviewSessionRepository sessionRepo;
    @Autowired SessionQuestionRepository sessionQuestionRepo;
    @Autowired OutboxEventRepository outboxRepo;
    @Autowired ObjectMapper objectMapper;

    @Test
    void fullFlow_loopsThroughBlueprint_finalizes_andStagesOverallReview() {
        // ── Stub Feign adapters ───────────────────────────────────────────────

        when(userProfileAdapter.getProfile(USER_ID)).thenReturn(profile("Backend", "Mid", 3));
        when(questionBankAdapter.markAskedSoft(anyString()))
                .thenAnswer(inv -> Optional.of(new MarkAskedResponse(
                        inv.getArgument(0), 1L, OffsetDateTime.now())));
        // QuestionPicker calls getSnapshotSoft on every pin — return a thin
        // BEHAVIORAL snapshot so the planner has the fields it needs.
        when(questionBankAdapter.getSnapshotSoft(anyString()))
                .thenAnswer(inv -> Optional.of(snapshotFor(inv.getArgument(0))));
        // Round-robin candidate generator: each filter() returns a different
        // question id so the planner can pin one per topic without dedup.
        AtomicCounter counter = new AtomicCounter();
        when(questionBankAdapter.filter(any())).thenAnswer(inv -> filterResponse(List.of(
                candidate("q-bank-" + counter.next(), QuestionType.BEHAVIORAL,
                        Difficulty.EASY, "Question " + counter.value(),
                        "OWNERSHIP", null, true, 0L)
        )));

        // ── Start session ─────────────────────────────────────────────────────

        StartSessionOutput started = sessionService.start(USER_ID,
                new StartSessionInput(InterviewType.BEHAVIORAL, null));
        UUID sessionId = started.sessionId();
        int questionBudget = started.questionBudget();
        assertThat(questionBudget).isGreaterThan(0);

        // ── Loop the blueprint: for each pinned question, fake a SCORED  ──────
        //    behavioral evaluation. The planner pins the next one, etc.,
        //    until the budget is exhausted and the planner emits EndSession
        //    → handleEndSession flips the session to COMPLETED.

        int safetyMax = questionBudget * 3; // follow-ups can extend the loop
        int loops = 0;
        while (true) {
            loops++;
            if (loops > safetyMax) {
                throw new AssertionError("Loop did not terminate within " + safetyMax + " iterations");
            }
            var pinnedQs = sessionQuestionRepo.findBySessionIdOrderBySequenceAsc(sessionId);
            // Find the next un-answered pinned question.
            UUID nextSqId = null;
            for (var sq : pinnedQs) {
                Optional<Answer> existing = answerRepo.findBySessionQuestionId(sq.getId());
                if (existing.isEmpty()) {
                    nextSqId = sq.getId();
                    break;
                }
            }
            if (nextSqId == null) {
                // No more un-answered pinned questions. Either the planner
                // ended the session naturally or we've fully covered the
                // blueprint — break out and let the gate fire below.
                break;
            }

            // Insert the answer in EVALUATING so applyEvaluationCompleted
            // can transition it to SCORED. Mirrors the state the answer
            // would be in after TranscriptConsumer.applyTranscriptReady ran.
            Answer answer = answerRepo.save(Answer.builder()
                    .sessionId(sessionId)
                    .sessionQuestionId(nextSqId)
                    .type(AnswerType.VIDEO)
                    .status(AnswerStatus.EVALUATING)
                    .submittedAt(OffsetDateTime.now())
                    .build());

            // Mock-AI verdict for this answer — STRONG so each topic closes
            // and the planner moves on.
            answerService.applyEvaluationCompleted(answer.getId(),
                    strongBehavioralPayload(sessionId));

            var s = sessionRepo.findById(sessionId).orElseThrow();
            if (s.getStatus() == SessionStatus.COMPLETED || s.getStatus() == SessionStatus.SCORED) {
                break;
            }
        }

        // ── Assert: session reached COMPLETED and outbox has SESSION_EVAL_REQUESTED

        var session = sessionRepo.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getMetadata())
                .as("metadata.sessionEvalRequestedAt is the dedup flag set by SessionFinalizerService")
                .containsKey("sessionEvalRequestedAt");

        OutboxEvent sessionEvalRequest = findOutbox(sessionId, KafkaTopics.SESSION_EVALUATION_REQUESTED);
        assertThat(sessionEvalRequest).isNotNull();
        assertThat(sessionEvalRequest.getEventType()).isEqualTo("SESSION_EVALUATION_REQUESTED");
        Map<String, Object> payload = sessionEvalRequest.getPayload();
        assertThat(payload.get("sessionId")).isEqualTo(sessionId.toString());
        assertThat(payload.get("interviewType")).isEqualTo("BEHAVIORAL");
        assertThat(payload.get("answers")).asList().isNotEmpty();
        assertThat(payload).containsKey("blueprint");

        // ── Assert: per-question results are still HIDDEN (session not SCORED yet)

        var firstAnswer = answerRepo.findBySessionId(sessionId).get(0);
        var pair = answerService.getForUser(sessionId, firstAnswer.getId(), USER_ID);
        AnswerView maskedView = AnswerView.fromEntity(pair.answer(), pair.questionType(),
                pair.sessionStatus() == SessionStatus.SCORED, objectMapper);
        assertThat(pair.answer().getScore())
                .as("entity carries the real score — planner needs it")
                .isNotNull();
        assertThat(maskedView.score())
                .as("DTO masks the score until session is SCORED")
                .isNull();
        assertThat(maskedView.feedback()).isNull();
        assertThat(maskedView.verdict()).isNull();

        // ── Apply overall review (simulates SessionEvaluationConsumer) ────────

        Map<String, Object> overall = new HashMap<>();
        overall.put("sessionId", sessionId.toString());
        overall.put("overallScore", 8.4f);
        overall.put("grade", "B");
        overall.put("hireSignal", "yes");
        overall.put("summary", "Strong across the board.");
        overall.put("strengths", List.of("ownership", "specificity"));
        overall.put("weaknesses", List.of("could go deeper on system design"));
        overall.put("perTopicSummary", List.of(
                Map.of("topicKind", "COMPETENCY", "topicValue", "OWNERSHIP",
                        "status", "STRONG", "comment", "consistent")));
        overall.put("recommendations", List.of("practise system-design drills"));

        sessionService.applyOverallReview(sessionId, overall);

        // ── Assert: session SCORED + finalScore + outbox INTERVIEW_SCORED ─────

        var scored = sessionRepo.findById(sessionId).orElseThrow();
        assertThat(scored.getStatus()).isEqualTo(SessionStatus.SCORED);
        assertThat(scored.getFinalScore()).isEqualTo(8.4f);
        assertThat(scored.getScoredAt()).isNotNull();
        assertThat(scored.getMetadata().get("overallReview"))
                .as("overallReview JSON stashed for SessionView projection")
                .isInstanceOf(Map.class);

        OutboxEvent interviewScored = findOutbox(sessionId, KafkaTopics.INTERVIEW_SCORED);
        assertThat(interviewScored).isNotNull();
        // The Float lands as-is on the in-memory entity (first-level cache);
        // the JSONB serializer would round-trip it to Double on a fresh read.
        assertThat(((Number) interviewScored.getPayload().get("finalScore")).floatValue())
                .isEqualTo(8.4f);
        assertThat(interviewScored.getPayload().get("grade")).isEqualTo("B");
        assertThat(interviewScored.getPayload().get("hireSignal")).isEqualTo("yes");

        // ── Assert: SessionView now exposes overallReview, AnswerView reveals  ─

        SessionView view = sessionService.getForUser(sessionId, USER_ID);
        assertThat(view.status()).isEqualTo(SessionStatus.SCORED);
        assertThat(view.finalScore()).isEqualTo(8.4f);
        assertThat(view.overallReview()).isNotNull();
        assertThat(view.overallReview().overallScore()).isEqualTo(8.4f);

        var revealedPair = answerService.getForUser(sessionId, firstAnswer.getId(), USER_ID);
        AnswerView revealedView = AnswerView.fromEntity(revealedPair.answer(),
                revealedPair.questionType(),
                revealedPair.sessionStatus() == SessionStatus.SCORED, objectMapper);
        assertThat(revealedView.score()).isNotNull();
        assertThat(revealedView.verdict()).isNotNull();
    }

    @Test
    void overallReview_isIdempotent_secondDeliveryNoOps() {
        // Build a quick COMPLETED + already-SCORED session, then apply overall
        // review again to make sure the second call is a no-op (no double
        // INTERVIEW_SCORED outbox row, no metadata stomp).
        when(userProfileAdapter.getProfile(USER_ID)).thenReturn(profile("Backend", "Mid", 3));
        when(questionBankAdapter.markAskedSoft(anyString()))
                .thenAnswer(inv -> Optional.of(new MarkAskedResponse(
                        inv.getArgument(0), 1L, OffsetDateTime.now())));
        when(questionBankAdapter.getSnapshotSoft(anyString()))
                .thenAnswer(inv -> Optional.of(snapshotFor(inv.getArgument(0))));
        when(questionBankAdapter.filter(any())).thenReturn(filterResponse(List.of(
                candidate("q-idem-1", QuestionType.BEHAVIORAL, Difficulty.EASY,
                        "Q1", "OWNERSHIP", null, true, 0L))));

        StartSessionOutput started = sessionService.start(USER_ID,
                new StartSessionInput(InterviewType.BEHAVIORAL, null));
        UUID sessionId = started.sessionId();

        // Force COMPLETED via the existing /finish path (the gate won't fire
        // because no answers are pinned yet, but the status flips correctly).
        var s = sessionRepo.findById(sessionId).orElseThrow();
        s.setStatus(SessionStatus.COMPLETED);
        s.setFinishedAt(OffsetDateTime.now());
        sessionRepo.save(s);

        Map<String, Object> overall = Map.of(
                "sessionId", sessionId.toString(),
                "overallScore", 7.0f,
                "grade", "B",
                "hireSignal", "yes",
                "summary", "ok");
        sessionService.applyOverallReview(sessionId, overall);
        long outboxAfterFirst = outboxRepo.findAll().stream()
                .filter(e -> KafkaTopics.INTERVIEW_SCORED.equals(e.getTopic())
                        && sessionId.equals(e.getAggregateId()))
                .count();
        assertThat(outboxAfterFirst).isEqualTo(1);

        // Second delivery — same payload, must be a no-op.
        sessionService.applyOverallReview(sessionId, Map.of(
                "sessionId", sessionId.toString(),
                "overallScore", 9.9f,  // Even a *different* score should not stomp.
                "grade", "A"));
        long outboxAfterSecond = outboxRepo.findAll().stream()
                .filter(e -> KafkaTopics.INTERVIEW_SCORED.equals(e.getTopic())
                        && sessionId.equals(e.getAggregateId()))
                .count();
        assertThat(outboxAfterSecond)
                .as("second applyOverallReview must be idempotent")
                .isEqualTo(1);
        var stillScored = sessionRepo.findById(sessionId).orElseThrow();
        assertThat(stillScored.getFinalScore()).isEqualTo(7.0f);
    }

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private OutboxEvent findOutbox(UUID sessionId, String topic) {
        return outboxRepo.findAll().stream()
                .filter(e -> topic.equals(e.getTopic()) && sessionId.equals(e.getAggregateId()))
                .findFirst()
                .orElse(null);
    }

    private static UserProfileResponse profile(String trackName, String levelName, int experience) {
        return new UserProfileResponse(
                USER_ID, "Finalize Tester",
                new PositionResponse("pos", "track-id", trackName, "level-id", levelName),
                "Hanoi", experience, null, null,
                List.of("java", "spring"), "vi", experience, List.of("fintech"));
    }

    private static QuestionFilterResponse filterResponse(List<QuestionCandidate> candidates) {
        return new QuestionFilterResponse(candidates, candidates.size());
    }

    private static QuestionCandidate candidate(
            String id, QuestionType type, Difficulty difficulty, String text,
            String competency, String domain, boolean isOpener, long askCount) {
        return new QuestionCandidate(
                id, type, difficulty, text, competency, domain,
                List.of("opener"), null, isOpener, askCount);
    }

    private static QuestionSnapshotResponse snapshotFor(String questionId) {
        return new QuestionSnapshotResponse(
                OffsetDateTime.now(),
                questionId, "BEHAVIORAL", "EASY", List.of("opener"),
                "Snapshot text for " + questionId, null,
                "OWNERSHIP",
                List.of("specific_recent_example", "names_own_role_clearly", "fact_based_argument"),
                null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    /** Mirror of the AI behavioral output — same shape used by InterviewFlowIT. */
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

    /** Tiny mutable counter so the answerer-side filter mock can give each pin a unique id. */
    private static class AtomicCounter {
        int n = 0;
        int next() { return ++n; }
        int value() { return n; }
    }
}
