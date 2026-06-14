package com.mockwise.questionbank.service;

import com.core.apiresponse.response.ApiListResponse;
import com.mockwise.questionbank.common.exception.BusinessException;
import com.mockwise.questionbank.common.exception.StatusCode;
import com.mockwise.questionbank.dto.request.BehavioralQuestionRequest;
import com.mockwise.questionbank.dto.request.CodingQuestionRequest;
import com.mockwise.questionbank.dto.request.CoreQuestionRequest;
import com.mockwise.questionbank.dto.response.*;
import com.mockwise.questionbank.dto.response.QuestionSnapshotResponse;
import com.mockwise.questionbank.entity.*;
import com.mockwise.questionbank.enums.*;
import com.mockwise.questionbank.mapper.BehavioralMapper;
import com.mockwise.questionbank.mapper.CodingMapper;
import com.mockwise.questionbank.mapper.CoreMapper;
import com.mockwise.questionbank.message.publisher.QuestionBankEventEmitter;
import com.mockwise.questionbank.repository.BehavioralQuestionRepository;
import com.mockwise.questionbank.repository.CodingQuestionRepository;
import com.mockwise.questionbank.repository.CoreQuestionRepository;
import com.mockwise.questionbank.repository.QuestionRepository;
import com.mockwise.questionbank.tts.TtsClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionService {

    QuestionRepository questionRepository;
    BehavioralQuestionRepository behavioralRepository;
    CoreQuestionRepository coreRepository;
    CodingQuestionRepository codingRepository;
    BehavioralMapper behavioralMapper;
    CoreMapper coreMapper;
    CodingMapper codingMapper;

    TtsClient ttsClient;
    QuestionBankEventEmitter eventEmitter;

    // ── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public BehavioralQuestionResponse createBehavioral(BehavioralQuestionRequest req, String createdBy) {
        Question base = behavioralMapper.toQuestion(req);
        base.setType(QuestionType.BEHAVIORAL);
        base.setCreatedBy(createdBy);
        questionRepository.save(base);

        BehavioralQuestion bq = behavioralMapper.toEntity(req);
        bq.setQuestion(base);

        TtsClient.TtsResult tts = ttsClient.synthesize(base.getId(), req.getText());
        if (tts.success()) {
            bq.setAudioKey(tts.objectKey());
        }
        behavioralRepository.save(bq);

        log.info("Created behavioral question id={} audio={}", base.getId(), bq.getAudioKey());
        return behavioralMapper.toResponse(bq);
    }

    @Transactional
    public CoreQuestionResponse createCore(CoreQuestionRequest req, String createdBy) {
        Question base = coreMapper.toQuestion(req);
        base.setType(QuestionType.CORE_CONCEPTUAL);
        base.setCreatedBy(createdBy);
        questionRepository.save(base);

        CoreQuestion cq = coreMapper.toEntity(req);
        cq.setQuestion(base);

        TtsClient.TtsResult tts = ttsClient.synthesize(base.getId(), req.getText());
        if (tts.success()) {
            cq.setAudioKey(tts.objectKey());
        }
        coreRepository.save(cq);

        log.info("Created core question id={} audio={}", base.getId(), cq.getAudioKey());
        return coreMapper.toResponse(cq);
    }

    @Transactional
    public CodingQuestionResponse createCoding(CodingQuestionRequest req, String createdBy) {
        Question base = codingMapper.toQuestion(req);
        base.setType(QuestionType.LIVE_CODING);
        base.setCreatedBy(createdBy);
        questionRepository.save(base);

        CodingQuestion cq = codingMapper.toEntity(req);
        cq.setQuestion(base);
        normalizeTestCaseIds(cq.getTestCases());
        codingRepository.save(cq);

        log.info("Created coding question id={}", base.getId());
        return codingMapper.toResponse(cq);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Object getById(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        return switch (base.getType()) {
            case BEHAVIORAL -> behavioralMapper.toResponse(
                    behavioralRepository.findById(id)
                            .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND)));
            case CORE_CONCEPTUAL -> coreMapper.toResponse(
                    coreRepository.findById(id)
                            .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND)));
            case LIVE_CODING -> codingMapper.toResponse(
                    codingRepository.findById(id)
                            .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND)));
        };
    }

    @Transactional(readOnly = true)
    public ApiListResponse<BehavioralQuestionResponse> getBehavioral(
            String competency, Difficulty difficulty, QuestionStatus status,
            List<String> tags, String q, Pageable pageable) {
        Page<BehavioralQuestion> page = behavioralRepository.findAllWithFilters(
                competency,
                difficulty == null ? null : difficulty.name(),
                status == null ? null : status.name(),
                toPostgresArray(tags),
                normalizeSearch(q),
                pageable);
        return ApiListResponse.of(
                page.getContent().stream().map(behavioralMapper::toResponse).toList(),
                (int) page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ApiListResponse<CoreQuestionResponse> getCore(
            String domain, String targetRole, Difficulty difficulty,
            QuestionStatus status, List<String> tags, String q, Pageable pageable) {
        Page<CoreQuestion> page = coreRepository.findAllWithFilters(
                domain,
                targetRole,
                difficulty == null ? null : difficulty.name(),
                status == null ? null : status.name(),
                toPostgresArray(tags),
                normalizeSearch(q),
                pageable);
        return ApiListResponse.of(
                page.getContent().stream().map(coreMapper::toResponse).toList(),
                (int) page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ApiListResponse<CodingQuestionResponse> getCoding(
            Difficulty difficulty, QuestionStatus status, List<String> tags, String q, Pageable pageable) {
        Page<CodingQuestion> page = codingRepository.findAllWithFilters(
                difficulty == null ? null : difficulty.name(),
                status == null ? null : status.name(),
                toPostgresArray(tags),
                normalizeSearch(q),
                pageable);
        return ApiListResponse.of(
                page.getContent().stream().map(codingMapper::toResponse).toList(),
                (int) page.getTotalElements());
    }

    /** Blank/whitespace keyword → null (no filter); otherwise trimmed. */
    private static String normalizeSearch(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public BehavioralQuestionResponse updateBehavioral(String id, BehavioralQuestionRequest req) {
        BehavioralQuestion bq = behavioralRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        boolean textChanged = !bq.getText().equals(req.getText());

        behavioralMapper.updateQuestion(bq.getQuestion(), req);
        questionRepository.save(bq.getQuestion());

        behavioralMapper.updateEntity(bq, req);
        if (textChanged) {
            TtsClient.TtsResult tts = ttsClient.synthesize(id, req.getText());
            bq.setAudioKey(tts.success() ? tts.objectKey() : null);
            log.info("Text changed for behavioral question id={} — audio_key={}", id, bq.getAudioKey());
        }

        BehavioralQuestion saved = behavioralRepository.save(bq);
        if (saved.getQuestion().getStatus() == QuestionStatus.ACTIVE) {
            eventEmitter.emitUpdated(saved.getQuestion());
        }
        return behavioralMapper.toResponse(saved);
    }

    @Transactional
    public CoreQuestionResponse updateCore(String id, CoreQuestionRequest req) {
        CoreQuestion cq = coreRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        boolean textChanged = !cq.getText().equals(req.getText());

        coreMapper.updateQuestion(cq.getQuestion(), req);
        questionRepository.save(cq.getQuestion());

        coreMapper.updateEntity(cq, req);
        if (textChanged) {
            TtsClient.TtsResult tts = ttsClient.synthesize(id, req.getText());
            cq.setAudioKey(tts.success() ? tts.objectKey() : null);
            log.info("Text changed for core question id={} — audio_key={}", id, cq.getAudioKey());
        }

        CoreQuestion saved = coreRepository.save(cq);
        if (saved.getQuestion().getStatus() == QuestionStatus.ACTIVE) {
            eventEmitter.emitUpdated(saved.getQuestion());
        }
        return coreMapper.toResponse(saved);
    }

    @Transactional
    public CodingQuestionResponse updateCoding(String id, CodingQuestionRequest req) {
        CodingQuestion cq = codingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        codingMapper.updateQuestion(cq.getQuestion(), req);
        questionRepository.save(cq.getQuestion());

        codingMapper.updateEntity(cq, req);
        normalizeTestCaseIds(cq.getTestCases());

        return codingMapper.toResponse(codingRepository.save(cq));
    }

    // ── Status & Delete ───────────────────────────────────────────────────────

    @Transactional
    public void updateStatus(String id, QuestionStatus status) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
        QuestionStatus previous = base.getStatus();
        base.setStatus(status);
        Question saved = questionRepository.save(base);
        log.info("Question id={} status changed: {} -> {}", id, previous, status);

        if (previous != status) {
            if (status == QuestionStatus.ACTIVE) {
                eventEmitter.emitActivated(saved);
            } else if (previous == QuestionStatus.ACTIVE) {
                eventEmitter.emitDeactivated(saved);
            }
        }
    }

    @Transactional
    public void delete(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        // Tell consumers (search index) to drop it before the row is gone.
        // Only ACTIVE questions are ever indexed, so others need no event.
        if (base.getStatus() == QuestionStatus.ACTIVE) {
            eventEmitter.emitDeactivated(base);
        }

        // Hard delete: the subtype row (coding/behavioral/core) and any
        // followups are removed via FK ON DELETE CASCADE on questions(id).
        questionRepository.delete(base);
        log.info("Question id={} hard-deleted", id);
    }

    // ── Downstream payloads ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ForAiResponse getForAi(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        return switch (base.getType()) {
            case BEHAVIORAL -> {
                BehavioralQuestion bq = behavioralRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
                yield ForAiResponse.fromBehavioral(
                        id, bq.getText(), bq.getCompetency().name(),
                        Arrays.asList(bq.getExpectedSignals()));
            }
            case CORE_CONCEPTUAL -> {
                CoreQuestion cq = coreRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
                yield ForAiResponse.fromCore(
                        id, cq.getText(), cq.getDomain().name(),
                        Arrays.asList(cq.getKeyConcepts()), cq.getDepthExpected());
            }
            case LIVE_CODING -> {
                CodingQuestion cq = codingRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
                yield ForAiResponse.fromCoding(
                        id, cq.getTitle(), cq.getDescription(),
                        base.getDifficulty().name(),
                        Arrays.asList(base.getTags()),
                        cq.getConstraints(),
                        cq.getOptimalTimeComplexity(),
                        cq.getOptimalSpaceComplexity());
            }
        };
    }

    @Transactional(readOnly = true)
    public ForJudgeResponse getForJudge(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        if (base.getType() != QuestionType.LIVE_CODING) {
            throw new BusinessException(StatusCode.FOR_JUDGE_NOT_SUPPORTED);
        }

        CodingQuestion cq = codingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        List<ForJudgeResponse.JudgeTestCase> judgeCases = cq.getTestCases().stream()
                .map(ForJudgeResponse.JudgeTestCase::from)
                .toList();

        ForJudgeResponse response = new ForJudgeResponse();
        response.setLanguage("java");
        response.setFunctionMeta(cq.getFunctionMeta());
        response.setTestCases(judgeCases);
        return response;
    }

    // ── Snapshot ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public QuestionSnapshotResponse getSnapshot(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        return switch (base.getType()) {
            case BEHAVIORAL -> behavioralMapper.toSnapshot(
                    behavioralRepository.findById(id)
                            .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND)));
            case CORE_CONCEPTUAL -> coreMapper.toSnapshot(
                    coreRepository.findById(id)
                            .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND)));
            case LIVE_CODING -> codingMapper.toSnapshot(
                    codingRepository.findById(id)
                            .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND)));
        };
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    @Transactional
    public void updateAudioKey(String id, String audioKey) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        switch (base.getType()) {
            case BEHAVIORAL -> {
                BehavioralQuestion bq = behavioralRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
                bq.setAudioKey(audioKey);
                behavioralRepository.save(bq);
            }
            case CORE_CONCEPTUAL -> {
                CoreQuestion cq = coreRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
                cq.setAudioKey(audioKey);
                coreRepository.save(cq);
            }
            case LIVE_CODING -> throw new BusinessException(StatusCode.AUDIO_NOT_SUPPORTED);
        }

        log.info("Audio key updated for question id={}", id);
    }

    /**
     * Re-run TTS for a BEHAVIORAL / CORE question from its current text and
     * persist the fresh object key. Lets an admin recover audio that failed to
     * generate (TTS outage at create time) or refresh a clip without having to
     * re-save the whole question. On TTS failure the audio key is left
     * untouched and the call surfaces an error so the admin can retry.
     *
     * @return the new audio object key
     */
    @Transactional
    public String regenerateAudio(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        return switch (base.getType()) {
            case BEHAVIORAL -> {
                BehavioralQuestion bq = behavioralRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
                TtsClient.TtsResult tts = ttsClient.synthesize(id, bq.getText());
                if (!tts.success()) {
                    throw new BusinessException(StatusCode.AUDIO_GENERATION_FAILED);
                }
                bq.setAudioKey(tts.objectKey());
                behavioralRepository.save(bq);
                log.info("Regenerated audio for behavioral question id={} audio_key={}", id, tts.objectKey());
                yield tts.objectKey();
            }
            case CORE_CONCEPTUAL -> {
                CoreQuestion cq = coreRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));
                TtsClient.TtsResult tts = ttsClient.synthesize(id, cq.getText());
                if (!tts.success()) {
                    throw new BusinessException(StatusCode.AUDIO_GENERATION_FAILED);
                }
                cq.setAudioKey(tts.objectKey());
                coreRepository.save(cq);
                log.info("Regenerated audio for core question id={} audio_key={}", id, tts.objectKey());
                yield tts.objectKey();
            }
            case LIVE_CODING -> throw new BusinessException(StatusCode.AUDIO_NOT_SUPPORTED);
        };
    }

    @Transactional(readOnly = true)
    public String getAudioKey(String id) {
        Question base = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND));

        String audioKey = switch (base.getType()) {
            case BEHAVIORAL -> behavioralRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND))
                    .getAudioKey();
            case CORE_CONCEPTUAL -> coreRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(StatusCode.QUESTION_NOT_FOUND))
                    .getAudioKey();
            case LIVE_CODING -> throw new BusinessException(StatusCode.AUDIO_NOT_SUPPORTED);
        };

        if (audioKey == null || audioKey.isBlank()) {
            throw new BusinessException(StatusCode.AUDIO_NOT_AVAILABLE);
        }

        return audioKey;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Test-case ids are persisted in a jsonb column and consumed downstream
     * (interview → judge) as {@link java.util.UUID}. AI-generated drafts arrive
     * with non-UUID ids ("tc-1", "tc-2", …), which break UUID deserialization in
     * judge-service. Replace any id that is null/blank or not a canonical UUID
     * with a freshly generated one; already-valid UUIDs are kept so ids stay
     * stable across edits.
     */
    private void normalizeTestCaseIds(List<TestCase> testCases) {
        if (testCases == null) return;
        for (TestCase tc : testCases) {
            if (!isCanonicalUuid(tc.getId())) {
                tc.setId(UUID.randomUUID().toString());
            }
        }
    }

    private boolean isCanonicalUuid(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Converts a List<String> into a PostgreSQL array literal: {"a","b","c"}
     * Returns null when the list is empty so the query skips the tags filter.
     */
    private String toPostgresArray(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        String joined = tags.stream()
                .map(t -> "\"" + t.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{" + joined + "}";
    }
}
