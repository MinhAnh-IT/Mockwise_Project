package com.mockwise.ttsstt.stt.service;

import com.mockwise.ttsstt.common.config.ElevenLabsProperties;
import com.mockwise.ttsstt.common.config.StorageClientProperties;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import com.mockwise.ttsstt.storage.PresignedDownload;
import com.mockwise.ttsstt.storage.StorageServiceClient;
import com.mockwise.ttsstt.stt.entity.SttJob;
import com.mockwise.ttsstt.stt.entity.SttJobStatus;
import com.mockwise.ttsstt.stt.entity.Transcript;
import com.mockwise.ttsstt.stt.kafka.TranscriptEventProducer;
import com.mockwise.ttsstt.stt.kafka.event.AnswerSubmittedEvent;
import com.mockwise.ttsstt.stt.kafka.event.TranscriptFailedEvent;
import com.mockwise.ttsstt.stt.kafka.event.TranscriptReadyEvent;
import com.mockwise.ttsstt.stt.repository.SttJobRepository;
import com.mockwise.ttsstt.stt.repository.TranscriptRepository;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Core STT pipeline. Used by both Kafka consumer (event-driven path) and the
 * internal REST controller (replay/admin path). Idempotent on
 * {@code storageObjectId}: returns the existing transcript when one is already
 * READY, unless {@code force} is true.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SttOrchestrator {

    SttJobRepository jobRepo;
    TranscriptRepository transcriptRepo;
    StorageServiceClient storageClient;
    FfmpegService ffmpegService;
    ElevenLabsScribeClient scribeClient;
    TranscriptEventProducer eventProducer;
    ElevenLabsProperties elevenProps;
    StorageClientProperties storageClientProps;
    RestClient minioClient;

    public SttOrchestrator(SttJobRepository jobRepo,
                           TranscriptRepository transcriptRepo,
                           StorageServiceClient storageClient,
                           FfmpegService ffmpegService,
                           ElevenLabsScribeClient scribeClient,
                           TranscriptEventProducer eventProducer,
                           ElevenLabsProperties elevenProps,
                           StorageClientProperties storageClientProps,
                           @Qualifier("minioDownloadRestClient") RestClient minioClient) {
        this.jobRepo = jobRepo;
        this.transcriptRepo = transcriptRepo;
        this.storageClient = storageClient;
        this.ffmpegService = ffmpegService;
        this.scribeClient = scribeClient;
        this.eventProducer = eventProducer;
        this.elevenProps = elevenProps;
        this.storageClientProps = storageClientProps;
        this.minioClient = minioClient;
    }

    public SttJob trigger(AnswerSubmittedEvent event, boolean force) {
        Optional<SttJob> existing = jobRepo.findByStorageObjectId(event.getStorageObjectId());
        if (existing.isPresent() && existing.get().getStatus() == SttJobStatus.READY && !force) {
            log.info("STT job already READY for storageObjectId={} — skipping", event.getStorageObjectId());
            return existing.get();
        }

        SttJob job = existing.orElseGet(() -> SttJob.builder()
                .storageObjectId(event.getStorageObjectId())
                .answerId(event.getAnswerId())
                .sessionId(event.getSessionId())
                .questionId(event.getQuestionId())
                .ownerUserId(event.getOwnerUserId())
                .status(SttJobStatus.PENDING)
                .attemptCount(0)
                .build());

        job.setStatus(SttJobStatus.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        SttJob saved = jobRepo.save(job);

        try {
            process(saved, event);
        } catch (BusinessException ex) {
            markFailed(saved, String.valueOf(ex.getCode()), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error processing STT job {}", saved.getId(), ex);
            markFailed(saved, "INTERNAL", ex.getMessage());
            throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, ex.getMessage());
        }
        return saved;
    }

    private void process(SttJob job, AnswerSubmittedEvent event) {
        AnswerSubmittedEvent.VideoMeta meta = event.getVideoMeta();
        String objectKey = (meta != null) ? meta.getObjectKey() : null;
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(StatusCode.STT_OBJECT_NOT_AVAILABLE);
        }

        PresignedDownload presigned = storageClient.requestDownloadUrl(
                "INTERVIEW_VIDEO", objectKey, storageClientProps.downloadTtlSeconds());

        Path videoTmp = null;
        Path audioTmp = null;
        try {
            videoTmp = downloadToTmp(presigned.getUrl(), "video");
            audioTmp = ffmpegService.extractAudio(videoTmp);

            ScribeResult result = scribeClient.transcribe(audioTmp, event.getLanguageHint());

            Transcript transcript = Transcript.builder()
                    .sttJobId(job.getId())
                    .storageObjectId(job.getStorageObjectId())
                    .answerId(job.getAnswerId())
                    .sessionId(job.getSessionId())
                    .questionId(job.getQuestionId())
                    .text(result.getText())
                    .languageCode(result.getLanguageCode() != null ? result.getLanguageCode() : "und")
                    .languageConfidence(result.getLanguageConfidence())
                    .words(result.getWords())
                    .durationMs(estimateDurationMs(result))
                    .modelId(elevenProps.sttModel())
                    .build();
            Transcript savedTranscript = transcriptRepo.save(transcript);

            job.setStatus(SttJobStatus.READY);
            job.setTranscriptId(savedTranscript.getId());
            job.setFinishedAt(OffsetDateTime.now());
            jobRepo.save(job);

            publishReady(job, savedTranscript);
        } finally {
            silentlyDelete(videoTmp);
            silentlyDelete(audioTmp);
        }
    }

    @Transactional
    public void markFailed(SttJob job, String errorCode, String errorMessage) {
        job.setStatus(SttJobStatus.FAILED);
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage);
        job.setFinishedAt(OffsetDateTime.now());
        jobRepo.save(job);
        publishFailed(job);
    }

    private void publishReady(SttJob job, Transcript transcript) {
        TranscriptReadyEvent event = TranscriptReadyEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TRANSCRIPT_READY")
                .occurredAt(OffsetDateTime.now())
                .transcriptId(transcript.getId())
                .sttJobId(job.getId())
                .answerId(job.getAnswerId())
                .storageObjectId(job.getStorageObjectId())
                .sessionId(job.getSessionId())
                .questionId(job.getQuestionId())
                .ownerUserId(job.getOwnerUserId())
                .languageCode(transcript.getLanguageCode())
                .durationMs(transcript.getDurationMs())
                .wordCount(transcript.getWords() == null ? 0 : transcript.getWords().size())
                .transcriptText(transcript.getText())
                .build();
        eventProducer.publishReady(event);
        job.setPublishedEventAt(OffsetDateTime.now());
        jobRepo.save(job);
    }

    private void publishFailed(SttJob job) {
        TranscriptFailedEvent event = TranscriptFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("TRANSCRIPT_FAILED")
                .occurredAt(OffsetDateTime.now())
                .sttJobId(job.getId())
                .answerId(job.getAnswerId())
                .storageObjectId(job.getStorageObjectId())
                .sessionId(job.getSessionId())
                .questionId(job.getQuestionId())
                .errorCode(job.getErrorCode())
                .errorMessage(job.getErrorMessage())
                .attemptCount(job.getAttemptCount())
                .build();
        eventProducer.publishFailed(event);
        job.setPublishedEventAt(OffsetDateTime.now());
        jobRepo.save(job);
    }

    private Path downloadToTmp(String url, String prefix) {
        try {
            Path tmp = Paths.get(System.getProperty("java.io.tmpdir"),
                    prefix + "-" + UUID.randomUUID() + ".bin");
            // Wrap in URI so Spring's RestClient does not re-encode the
            // already-encoded MinIO presigned URL. The `.uri(String)`
            // overload runs the value through UriComponentsBuilder which
            // double-encodes `%2F` → `%252F`, corrupting the
            // {@code X-Amz-Credential} param and turning the request into
            // a "AuthorizationQueryParametersError" from MinIO.
            byte[] bytes = minioClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
            if (bytes == null) {
                throw new BusinessException(StatusCode.STT_OBJECT_NOT_AVAILABLE);
            }
            Files.write(tmp, bytes);
            return tmp;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to download from MinIO presigned URL", ex);
            throw new BusinessException(StatusCode.STT_OBJECT_NOT_AVAILABLE);
        }
    }

    private Integer estimateDurationMs(ScribeResult result) {
        if (result.getWords() == null || result.getWords().isEmpty()) return null;
        return result.getWords().stream()
                .map(w -> w.getEnd())
                .filter(Objects::nonNull)
                .reduce((a, b) -> b)
                .map(d -> (int) Math.round(d * 1000))
                .orElse(null);
    }

    private void silentlyDelete(Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (Exception ex) {
            log.warn("Failed to delete tmp file {}", p, ex);
        }
    }

    /** Background sweeper: re-publish events for jobs whose publication may have been lost. */
    @Transactional
    public void republishMissedEvents() {
        List<SttJob> orphans = jobRepo.findByStatusInAndPublishedEventAtIsNull(
                List.of(SttJobStatus.READY, SttJobStatus.FAILED));
        for (SttJob job : orphans) {
            try {
                if (job.getStatus() == SttJobStatus.READY && job.getTranscriptId() != null) {
                    transcriptRepo.findById(job.getTranscriptId())
                            .ifPresent(t -> publishReady(job, t));
                } else if (job.getStatus() == SttJobStatus.FAILED) {
                    publishFailed(job);
                }
            } catch (Exception ex) {
                log.warn("Failed to republish event for job {}", job.getId(), ex);
            }
        }
    }
}
