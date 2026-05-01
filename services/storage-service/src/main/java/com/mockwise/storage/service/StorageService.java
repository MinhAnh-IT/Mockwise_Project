package com.mockwise.storage.service;

import com.mockwise.storage.common.config.MinioProperties;
import com.mockwise.storage.common.exception.BusinessException;
import com.mockwise.storage.common.exception.StatusCode;
import com.mockwise.storage.dto.request.CreateVideoUploadRequest;
import com.mockwise.storage.dto.request.InternalDownloadUrlRequest;
import com.mockwise.storage.dto.response.PresignedUrlResponse;
import com.mockwise.storage.dto.response.QuestionAudioUploadResponse;
import com.mockwise.storage.dto.response.StorageObjectResponse;
import com.mockwise.storage.dto.response.VideoUploadResponse;
import com.mockwise.storage.entity.StorageObject;
import com.mockwise.storage.enums.StorageKind;
import com.mockwise.storage.enums.StorageStatus;
import com.mockwise.storage.gateway.MinioStorageGateway;
import com.mockwise.storage.repository.StorageObjectRepository;
import io.minio.StatObjectResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageService {

    StorageObjectRepository storageObjectRepository;
    MinioStorageGateway minioGateway;
    MinioProperties properties;

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/webm", "video/mp4", "video/x-matroska", "video/quicktime");

    private static final Set<String> ALLOWED_AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/mp4", "audio/wav", "audio/ogg", "audio/webm");

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    // ── Video upload (user-facing) ───────────────────────────────────────────

    /**
     * Reserves a video upload slot and returns a presigned PUT URL.
     *
     * <p>Object key is built server-side from the authenticated user id and session id —
     * the client never controls where the file lands, which is the core defense against
     * cross-user object enumeration.
     */
    @Transactional
    public VideoUploadResponse createVideoUpload(CreateVideoUploadRequest req, String ownerUserId) {
        validateVideoUpload(req);

        String bucket = properties.buckets().interviewVideo();
        String objectKey = "%s/%s/%s".formatted(ownerUserId, req.getSessionId(), UUID.randomUUID());

        StorageObject saved = storageObjectRepository.save(StorageObject.builder()
                .kind(StorageKind.INTERVIEW_VIDEO)
                .bucket(bucket)
                .objectKey(objectKey)
                .contentType(req.getContentType())
                .sizeBytes(req.getSizeBytes())
                .status(StorageStatus.PENDING_UPLOAD)
                .ownerUserId(ownerUserId)
                .sessionId(req.getSessionId())
                .build());

        int ttl = properties.presign().uploadTtlSeconds();
        String url = minioGateway.presignPutUrl(bucket, objectKey, ttl);

        log.info("Reserved video upload id={} session={} owner={}", saved.getId(), req.getSessionId(), ownerUserId);

        return VideoUploadResponse.builder()
                .objectId(saved.getId())
                .bucket(bucket)
                .objectKey(objectKey)
                .uploadUrl(url)
                .expiresAt(OffsetDateTime.now().plusSeconds(ttl))
                .build();
    }

    /**
     * Confirms an upload finished by stat-ing MinIO and flipping status to READY.
     *
     * <p>Verifies the bytes actually landed and the size matches what the client declared,
     * so a malicious client can't claim "uploaded" without putting anything.
     */
    @Transactional
    public StorageObjectResponse completeVideoUpload(String objectId, String ownerUserId) {
        StorageObject obj = storageObjectRepository.findById(objectId)
                .orElseThrow(() -> new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND));

        // Reject anything that isn't a user-uploaded video — return NOT_FOUND, not 403,
        // so we don't leak that an object with this id exists for another resource kind.
        if (obj.getKind() != StorageKind.INTERVIEW_VIDEO) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND);
        }
        if (!Objects.equals(obj.getOwnerUserId(), ownerUserId)) {
            throw new BusinessException(StatusCode.OWNERSHIP_VIOLATION);
        }
        if (obj.getStatus() == StorageStatus.READY) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_ALREADY_COMPLETED);
        }

        StatObjectResponse stat = minioGateway.stat(obj.getBucket(), obj.getObjectKey())
                .orElseThrow(() -> new BusinessException(StatusCode.STORAGE_UPLOAD_NOT_FOUND_IN_BUCKET));

        if (obj.getSizeBytes() != null && stat.size() != obj.getSizeBytes()) {
            log.warn("Size mismatch for object {} — declared {}, actual {}",
                    objectId, obj.getSizeBytes(), stat.size());
            throw new BusinessException(StatusCode.STORAGE_UPLOAD_SIZE_MISMATCH);
        }

        obj.setSizeBytes(stat.size());
        obj.setStatus(StorageStatus.READY);
        obj.setCompletedAt(OffsetDateTime.now());
        storageObjectRepository.save(obj);

        log.info("Completed video upload id={} size={}", objectId, stat.size());
        return StorageObjectResponse.from(obj);
    }

    // ── Avatar upload (user-facing) ──────────────────────────────────────────

    /**
     * Stores a user avatar in one round-trip via multipart upload.
     *
     * <p>Avatars are small (≤5MB) so streaming through this service to MinIO
     * keeps the architecture simple — no need to expose MinIO to the public
     * internet over HTTPS or set up cross-origin CORS for browser-direct PUTs.
     *
     * <p>The object key is server-built as {@code avatars/{userId}/{uuid}} so
     * the client never controls the storage path. The user-profile-service
     * stores the returned key on the user's profile; previous objects become
     * orphaned and can be cleaned up by a future sweep job.
     */
    @Transactional
    public StorageObjectResponse uploadAvatar(MultipartFile file, String ownerUserId) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new BusinessException(StatusCode.INVALID_CONTENT_TYPE, contentType, StorageKind.USER_AVATAR);
        }
        if (file.getSize() > properties.uploadLimits().avatarMaxBytes()) {
            throw new BusinessException(StatusCode.UPLOAD_SIZE_EXCEEDED,
                    file.getSize(), properties.uploadLimits().avatarMaxBytes(), StorageKind.USER_AVATAR);
        }

        String bucket = properties.buckets().userAvatar();
        String objectKey = "avatars/%s/%s".formatted(ownerUserId, UUID.randomUUID());

        try (var stream = file.getInputStream()) {
            minioGateway.putObject(bucket, objectKey, stream, file.getSize(), contentType);
        } catch (IOException e) {
            log.error("Failed to read avatar upload stream: {}", e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }

        StorageObject saved = storageObjectRepository.save(StorageObject.builder()
                .kind(StorageKind.USER_AVATAR)
                .bucket(bucket)
                .objectKey(objectKey)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .status(StorageStatus.READY)
                .ownerUserId(ownerUserId)
                .completedAt(OffsetDateTime.now())
                .build());

        log.info("Uploaded avatar id={} owner={} size={}", saved.getId(), ownerUserId, file.getSize());
        return StorageObjectResponse.from(saved);
    }

    // ── Question audio (internal: tts-stt → storage) ─────────────────────────

    /**
     * Stores a TTS-generated audio clip for a question.
     *
     * <p>Called server-to-server by tts-stt. Object key is a pure UUID — no admin
     * identity is encoded — because the audio belongs to the question, not the admin.
     */
    @Transactional
    public QuestionAudioUploadResponse uploadQuestionAudio(String questionId, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AUDIO_TYPES.contains(contentType)) {
            throw new BusinessException(StatusCode.INVALID_CONTENT_TYPE, contentType, StorageKind.QUESTION_AUDIO);
        }
        if (file.getSize() > properties.uploadLimits().audioMaxBytes()) {
            throw new BusinessException(StatusCode.UPLOAD_SIZE_EXCEEDED,
                    file.getSize(), properties.uploadLimits().audioMaxBytes(), StorageKind.QUESTION_AUDIO);
        }

        String bucket = properties.buckets().questionAudio();
        String objectKey = "audio/%s".formatted(UUID.randomUUID());

        try (var stream = file.getInputStream()) {
            minioGateway.putObject(bucket, objectKey, stream, file.getSize(), contentType);
        } catch (IOException e) {
            log.error("Failed to read audio upload stream: {}", e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }

        StorageObject saved = storageObjectRepository.save(StorageObject.builder()
                .kind(StorageKind.QUESTION_AUDIO)
                .bucket(bucket)
                .objectKey(objectKey)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .status(StorageStatus.READY)
                .questionId(questionId)
                .completedAt(OffsetDateTime.now())
                .build());

        log.info("Stored question audio id={} question={} size={}", saved.getId(), questionId, file.getSize());

        return QuestionAudioUploadResponse.builder()
                .objectId(saved.getId())
                .bucket(bucket)
                .objectKey(objectKey)
                .sizeBytes(file.getSize())
                .build();
    }

    // ── Presigned download (internal: interview-service → storage) ───────────

    /**
     * Returns a short-lived presigned GET URL for an existing object.
     *
     * <p>The caller (interview-service) is the ACL gatekeeper — this method assumes
     * the caller has already verified the user is allowed to access the object.
     * TTL is capped per kind so leaked URLs expire quickly.
     */
    @Transactional(readOnly = true)
    public PresignedUrlResponse createDownloadUrl(InternalDownloadUrlRequest req) {
        String bucket = bucketFor(req.getKind());
        int ttl = capTtl(req.getKind(), req.getTtlSeconds());

        StorageObject obj = storageObjectRepository.findByBucketAndObjectKey(bucket, req.getObjectKey())
                .orElseThrow(() -> new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND));

        if (obj.getStatus() != StorageStatus.READY) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND);
        }

        String url = minioGateway.presignGetUrl(bucket, req.getObjectKey(), ttl);
        log.info("Issued GET URL for {}/{} ttl={}s", bucket, req.getObjectKey(), ttl);

        return PresignedUrlResponse.builder()
                .url(url)
                .expiresAt(OffsetDateTime.now().plusSeconds(ttl))
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void validateVideoUpload(CreateVideoUploadRequest req) {
        if (!ALLOWED_VIDEO_TYPES.contains(req.getContentType())) {
            throw new BusinessException(StatusCode.INVALID_CONTENT_TYPE,
                    req.getContentType(), StorageKind.INTERVIEW_VIDEO);
        }
        if (req.getSizeBytes() > properties.uploadLimits().videoMaxBytes()) {
            throw new BusinessException(StatusCode.UPLOAD_SIZE_EXCEEDED,
                    req.getSizeBytes(), properties.uploadLimits().videoMaxBytes(),
                    StorageKind.INTERVIEW_VIDEO);
        }
    }

    private String bucketFor(StorageKind kind) {
        return switch (kind) {
            case INTERVIEW_VIDEO -> properties.buckets().interviewVideo();
            case QUESTION_AUDIO -> properties.buckets().questionAudio();
            case USER_AVATAR -> properties.buckets().userAvatar();
        };
    }

    /**
     * Caps download TTL per-kind so callers can't ask for arbitrarily long-lived URLs.
     * Audio is hard-capped at 60s by design — see design doc.
     */
    private int capTtl(StorageKind kind, int requested) {
        int max = switch (kind) {
            case QUESTION_AUDIO -> properties.presign().audioTtlSeconds();
            case INTERVIEW_VIDEO -> properties.presign().downloadTtlSeconds();
            case USER_AVATAR -> properties.presign().avatarTtlSeconds();
        };
        return Math.min(Math.max(requested, 1), max);
    }
}
