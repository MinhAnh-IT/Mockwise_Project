package com.mockwise.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.client.storage.StorageAdapter;
import com.mockwise.interview.client.storage.dto.StorageObjectResponse;
import com.mockwise.interview.client.userprofile.UserProfileAdapter;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.dto.request.SubmitAnswerInput;
import com.mockwise.interview.dto.response.SubmitAnswerOutput;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.message.constants.KafkaTopics;
import com.mockwise.interview.repository.AnswerRepository;
import com.mockwise.interview.repository.InterviewSessionRepository;
import com.mockwise.interview.repository.SessionQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the Lever 2 fast path + the fast-path/background race
 * (realtime-stt-plan.md §8.2, §11). Pure Mockito — no Spring context / DB —
 * so the state-machine branches are pinned without the integration harness.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnswerServiceRealtimeSttTest {

    private static final String USER = "user-1";

    @Mock AnswerRepository answerRepo;
    @Mock com.mockwise.interview.repository.AnswerEventLogRepository answerEventLogRepo;
    @Mock SessionQuestionRepository sessionQuestionRepo;
    @Mock com.mockwise.interview.repository.SessionTopicStateRepository topicStateRepo;
    @Mock InterviewSessionRepository sessionRepo;
    @Mock com.mockwise.interview.repository.InterviewBlueprintRepository blueprintRepo;
    @Mock StorageAdapter storageAdapter;
    @Mock UserProfileAdapter userProfileAdapter;
    @Mock com.mockwise.interview.client.ttsstt.TtsSttAdapter ttsSttAdapter;
    @Mock OutboxWriter outboxWriter;
    @Mock com.mockwise.interview.mapper.AssessmentVerdictMapper verdictMapper;
    @Mock com.mockwise.interview.planner.NextQuestionPlanner planner;
    @Mock SideEffectApplier sideEffectApplier;
    @Mock QuestionPicker questionPicker;
    @Mock SessionFinalizerService sessionFinalizer;
    @Mock SessionService sessionService;
    @Mock ObjectMapper objectMapper;

    @InjectMocks AnswerService service;

    private UUID sessionId;
    private UUID sqId;
    private InterviewSession session;
    private SessionQuestion sq;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "realtimeSttEnabled", true);
        sessionId = UUID.randomUUID();
        sqId = UUID.randomUUID();
        session = InterviewSession.builder()
                .id(sessionId).userId(USER).status(SessionStatus.IN_PROGRESS).build();
        sq = SessionQuestion.builder()
                .id(sqId).sessionId(sessionId).sequence(1)
                .questionType(QuestionType.BEHAVIORAL)
                .inlineText("Tell me about a conflict")
                .snapshot(Map.of("competency", "communication"))
                .build();
        // No profile snapshot → loadProfile hits the adapter; make it throw so
        // stageSpokenEvaluation falls back to the spoken-language hint (keeps
        // the test free of a full UserProfileResponse fixture).
        when(userProfileAdapter.getProfile(any())).thenThrow(new RuntimeException("no profile in test"));
        when(answerRepo.save(any(Answer.class))).thenAnswer(inv -> {
            Answer a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
    }

    private SubmitAnswerInput videoWithTranscript(String text) {
        return new SubmitAnswerInput(AnswerType.VIDEO, null, null, null,
                text, "vi", 42_000, "REALTIME_WEBSPEECH");
    }

    // ── Fast path submit ─────────────────────────────────────────────────────

    @Test
    void submit_fastPath_createsEvaluatingAnswer_stagesEval_noStorageNeeded() {
        when(sessionRepo.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));
        when(sessionQuestionRepo.findById(sqId)).thenReturn(java.util.Optional.of(sq));
        when(answerRepo.existsBySessionQuestionId(sqId)).thenReturn(false);

        SubmitAnswerOutput out = service.submit(
                sessionId, sqId, videoWithTranscript("This is my full spoken answer"), USER);

        assertThat(out.status()).isEqualTo(AnswerStatus.EVALUATING);
        assertThat(out.nextQuestion()).isNull();
        // Scored off the transcript — no storage round-trip at submit.
        verify(storageAdapter, never()).getObject(any());
        // Evaluation requested straight away; no answer-submitted (no batch STT yet).
        verify(outboxWriter).stage(eq(KafkaTopics.EVALUATION_REQUESTED), eq("EVALUATION_REQUESTED"), any(), any());
        verify(outboxWriter, never()).stage(eq(KafkaTopics.ANSWER_SUBMITTED), any(), any(), any());
        org.mockito.ArgumentCaptor<Answer> saved = org.mockito.ArgumentCaptor.forClass(Answer.class);
        verify(answerRepo).save(saved.capture());
        assertThat(saved.getValue().getRealtimeTranscript()).isEqualTo("This is my full spoken answer");
        assertThat(saved.getValue().getTranscriptSource()).isEqualTo("REALTIME_WEBSPEECH");
        assertThat(saved.getValue().getStorageObjectId()).isNull();
    }

    @Test
    void submit_fastPathRetry_returnsExistingAnswer_doesNotDuplicateOrStage() {
        when(sessionRepo.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));
        when(sessionQuestionRepo.findById(sqId)).thenReturn(java.util.Optional.of(sq));
        UUID existingId = UUID.randomUUID();
        Answer existing = Answer.builder()
                .id(existingId).sessionId(sessionId).sessionQuestionId(sqId)
                .type(AnswerType.VIDEO).status(AnswerStatus.EVALUATING)
                .submittedAt(java.time.OffsetDateTime.now()).build();
        when(answerRepo.findBySessionQuestionId(sqId)).thenReturn(java.util.Optional.of(existing));

        SubmitAnswerOutput out = service.submit(
                sessionId, sqId, videoWithTranscript("retried full spoken answer"), USER);

        assertThat(out.answerId()).isEqualTo(existingId);       // same answerId for attach (§8.4 #11)
        verify(answerRepo, never()).save(any());                 // no second row
        verify(outboxWriter, never()).stage(any(), any(), any(), any()); // no duplicate eval
    }

    @Test
    void submit_flagOff_ignoresTranscript_fallsToLegacy_requiresStorage() {
        ReflectionTestUtils.setField(service, "realtimeSttEnabled", false);
        when(sessionRepo.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));
        when(sessionQuestionRepo.findById(sqId)).thenReturn(java.util.Optional.of(sq));

        // Legacy VIDEO path needs a storageObjectId — absent here → validation error.
        assertThatThrownBy(() -> service.submit(sessionId, sqId, videoWithTranscript("hello"), USER))
                .isInstanceOf(BusinessException.class);
        verify(outboxWriter, never()).stage(any(), any(), any(), any());
    }

    // ── applyTranscriptReady race (§8.2 #5/#6) ───────────────────────────────

    @Test
    void transcriptReady_onEvaluating_storesAuthoritative_doesNotScoreOrStage() {
        Answer answer = Answer.builder()
                .id(UUID.randomUUID()).sessionId(sessionId).sessionQuestionId(sqId)
                .type(AnswerType.VIDEO).status(AnswerStatus.EVALUATING)
                .realtimeTranscript("realtime text used to score").build();
        when(answerRepo.findById(answer.getId())).thenReturn(java.util.Optional.of(answer));

        service.applyTranscriptReady(answer.getId(), UUID.randomUUID(), "vi", "batch text from video", 40_000);

        assertThat(answer.getStatus()).isEqualTo(AnswerStatus.EVALUATING); // unchanged
        assertThat(answer.getAuthoritativeTranscript()).isEqualTo("batch text from video");
        verify(outboxWriter, never()).stage(any(), any(), any(), any()); // no eval re-trigger
        verify(sessionQuestionRepo, never()).findById(any());            // legacy path not taken
    }

    @Test
    void transcriptReady_onScored_isPureNoOpBeyondAuthoritative() {
        Answer answer = Answer.builder()
                .id(UUID.randomUUID()).sessionId(sessionId).sessionQuestionId(sqId)
                .type(AnswerType.VIDEO).status(AnswerStatus.SCORED)
                .score(8.0f).realtimeTranscript("rt").build();
        when(answerRepo.findById(answer.getId())).thenReturn(java.util.Optional.of(answer));

        service.applyTranscriptReady(answer.getId(), UUID.randomUUID(), "vi", "batch", 1000);

        assertThat(answer.getStatus()).isEqualTo(AnswerStatus.SCORED); // no status change
        assertThat(answer.getScore()).isEqualTo(8.0f);                 // no re-score
        assertThat(answer.getAuthoritativeTranscript()).isEqualTo("batch");
        verify(sessionFinalizer, never()).maybeRequestOverallReview(any());
        verify(outboxWriter, never()).stage(any(), any(), any(), any());
    }

    @Test
    void transcriptReady_onProcessing_runsLegacyScoring() {
        Answer answer = Answer.builder()
                .id(UUID.randomUUID()).sessionId(sessionId).sessionQuestionId(sqId)
                .type(AnswerType.VIDEO).status(AnswerStatus.PROCESSING).build();
        when(answerRepo.findById(answer.getId())).thenReturn(java.util.Optional.of(answer));
        when(sessionQuestionRepo.findById(sqId)).thenReturn(java.util.Optional.of(sq));
        when(sessionRepo.findById(sessionId)).thenReturn(java.util.Optional.of(session));

        service.applyTranscriptReady(answer.getId(), UUID.randomUUID(), "vi", "the spoken answer", 30_000);

        assertThat(answer.getStatus()).isEqualTo(AnswerStatus.EVALUATING);
        assertThat(answer.getTranscriptSource()).isEqualTo("BATCH_SCRIBE");
        assertThat(answer.getAuthoritativeTranscript()).isEqualTo("the spoken answer");
        verify(outboxWriter).stage(eq(KafkaTopics.EVALUATION_REQUESTED), eq("EVALUATION_REQUESTED"), any(), any());
    }

    // ── attach-video (§8.2 #7, §11) ──────────────────────────────────────────

    @Test
    void attachVideo_setsObject_stagesAnswerSubmitted_once_andWorksAfterCompleted() {
        UUID objectId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        // Session COMPLETED — attach must still succeed (no IN_PROGRESS guard).
        InterviewSession completed = InterviewSession.builder()
                .id(sessionId).userId(USER).status(SessionStatus.COMPLETED).build();
        Answer answer = Answer.builder()
                .id(answerId).sessionId(sessionId).sessionQuestionId(sqId)
                .type(AnswerType.VIDEO).status(AnswerStatus.SCORED).build();
        when(sessionRepo.findById(sessionId)).thenReturn(java.util.Optional.of(completed));
        when(answerRepo.findById(answerId)).thenReturn(java.util.Optional.of(answer));
        when(sessionQuestionRepo.findById(sqId)).thenReturn(java.util.Optional.of(sq));
        when(storageAdapter.getObject(objectId)).thenReturn(new StorageObjectResponse(
                objectId.toString(), USER, "INTERVIEW_VIDEO", "READY",
                "bucket", "key.webm", "video/webm", 1024L, sessionId.toString(), null));

        service.attachVideo(sessionId, answerId, objectId, USER);
        // Second call (FE double-finish / retry) — must be a no-op.
        service.attachVideo(sessionId, answerId, objectId, USER);

        assertThat(answer.getStorageObjectId()).isEqualTo(objectId);
        verify(sessionService, never()).ensureWithinTimeBudget(any());
        verify(outboxWriter, times(1)).stage(eq(KafkaTopics.ANSWER_SUBMITTED), any(), any(), any());
    }
}
