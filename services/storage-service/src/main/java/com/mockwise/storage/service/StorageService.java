package com.mockwise.storage.service;

import com.mockwise.storage.common.config.MinioProperties;
import com.mockwise.storage.common.exception.BusinessException;
import com.mockwise.storage.common.exception.StatusCode;
import com.mockwise.storage.dto.request.CreateVideoUploadRequest;
import com.mockwise.storage.dto.request.InternalDownloadUrlRequest;
import com.mockwise.storage.dto.response.AvatarStream;
import com.mockwise.storage.dto.response.PresignedUrlResponse;
import com.mockwise.storage.dto.response.QuestionAudioUploadResponse;
import com.mockwise.storage.dto.response.StorageObjectResponse;
import com.mockwise.storage.dto.response.VideoUploadResponse;
import com.mockwise.storage.entity.StorageObject;
import com.mockwise.storage.enums.StorageKind;
import com.mockwise.storage.enums.StorageStatus;
import com.mockwise.storage.gateway.MinioStorageGateway;
import com.mockwise.storage.repository.StorageObjectRepository;
import io.minio.GetObjectResponse;
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

    /**
     * Streams the user's interview-video bytes through this service in a single
     * multipart POST. Mirrors the avatar pattern — see {@link #uploadAvatar} —
     * because the browser-direct presigned PUT to MinIO trips mixed-content
     * blocking when the app is served over HTTPS but MinIO is HTTP-only.
     *
     * <p>The object lands in the same place {@link #createVideoUpload} would
     * have written it, with status {@code READY} on return so the caller can
     * forward {@code objectId} straight to interview-service. Re-uses
     * {@link #ALLOWED_VIDEO_TYPES} so the validator stays consistent with the
     * presigned-PUT path.
     */
    @Transactional
    public StorageObjectResponse uploadVideoMultipart(
            MultipartFile file, String sessionId, String ownerUserId) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType)) {
            throw new BusinessException(StatusCode.INVALID_CONTENT_TYPE,
                    contentType, StorageKind.INTERVIEW_VIDEO);
        }
        if (file.getSize() > properties.uploadLimits().videoMaxBytes()) {
            throw new BusinessException(StatusCode.UPLOAD_SIZE_EXCEEDED,
                    file.getSize(), properties.uploadLimits().videoMaxBytes(),
                    StorageKind.INTERVIEW_VIDEO);
        }

        String bucket = properties.buckets().interviewVideo();
        String objectKey = "%s/%s/%s".formatted(ownerUserId, sessionId, UUID.randomUUID());

        try (var stream = file.getInputStream()) {
            minioGateway.putObject(bucket, objectKey, stream, file.getSize(), contentType);
        } catch (IOException e) {
            log.error("Failed to read video upload stream: {}", e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }

        StorageObject saved = storageObjectRepository.save(StorageObject.builder()
                .kind(StorageKind.INTERVIEW_VIDEO)
                .bucket(bucket)
                .objectKey(objectKey)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .status(StorageStatus.READY)
                .ownerUserId(ownerUserId)
                .sessionId(sessionId)
                .completedAt(OffsetDateTime.now())
                .build());

        log.info("Uploaded video id={} session={} owner={} size={}",
                saved.getId(), sessionId, ownerUserId, file.getSize());
        return StorageObjectResponse.from(saved);
    }

    /**
     * Streams a question-audio clip identified by its MinIO object key. Mirrors
     * {@link #streamLatestAvatar} — the browser fetches the bytes from this
     * service over HTTPS, side-stepping mixed-content blocking on the
     * MinIO-direct presigned URLs the FE used to consume.
     *
     * <p>Lookup is by {@code objectKey} (not numeric id) because that's what
     * interview-service already snapshots into {@code session_question.snapshot}
     * — no extra ID round-trip required.
     */
    @Transactional(readOnly = true)
    public AvatarStream streamQuestionAudio(String objectKey) {
        StorageObject obj = storageObjectRepository
                .findByBucketAndObjectKey(properties.buckets().questionAudio(), objectKey)
                .orElseThrow(() -> new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND));
        if (obj.getKind() != StorageKind.QUESTION_AUDIO) {
            // The (bucket, key) pair is unique per kind already, but defend
            // against an admin tool ever cross-writing to the wrong bucket.
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND);
        }
        if (obj.getStatus() != StorageStatus.READY) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND);
        }

        GetObjectResponse stream = minioGateway.getObject(obj.getBucket(), obj.getObjectKey());
        return new AvatarStream(stream, obj.getContentType(),
                obj.getSizeBytes() != null ? obj.getSizeBytes() : -1);
    }

    /**
     * Streams a user's submitted answer video back to them. Mirrors
     * {@link #streamQuestionAudio} / {@link #streamLatestAvatar} — proxies
     * MinIO bytes through HTTPS so the browser doesn't see the HTTP-only
     * MinIO host (mixed-content blocking on {@code <video>}).
     *
     * <p>ACL: object's {@code ownerUserId} must equal the requester's id.
     * Mismatches return {@link StatusCode#STORAGE_OBJECT_NOT_FOUND} (not 403)
     * so a probing user can't tell whether the id belongs to someone else
     * or doesn't exist at all.
     */
    @Transactional(readOnly = true)
    public AvatarStream streamInterviewVideo(String objectId, String requesterUserId) {
        StorageObject obj = storageObjectRepository.findById(objectId)
                .orElseThrow(() -> new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND));

        if (obj.getKind() != StorageKind.INTERVIEW_VIDEO) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND);
        }
        if (obj.getStatus() != StorageStatus.READY) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND);
        }
        if (!Objects.equals(obj.getOwnerUserId(), requesterUserId)) {
            throw new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND);
        }

        GetObjectResponse stream = minioGateway.getObject(obj.getBucket(), obj.getObjectKey());
        return new AvatarStream(stream, obj.getContentType(),
                obj.getSizeBytes() != null ? obj.getSizeBytes() : -1);
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

    /**
     * Streams the latest READY avatar bytes for a user. Server-side proxy to
     * MinIO so the browser, which loads the app over HTTPS, never sees the
     * HTTP-only MinIO host (mixed content).
     */
    @Transactional(readOnly = true)
    public AvatarStream streamLatestAvatar(String ownerUserId) {
        StorageObject obj = storageObjectRepository
                .findFirstByOwnerUserIdAndKindAndStatusOrderByCompletedAtDesc(
                        ownerUserId, StorageKind.USER_AVATAR, StorageStatus.READY)
                .orElseThrow(() -> new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND));

        GetObjectResponse stream = minioGateway.getObject(obj.getBucket(), obj.getObjectKey());
        return new AvatarStream(stream, obj.getContentType(),
                obj.getSizeBytes() != null ? obj.getSizeBytes() : -1);
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

    // ── Object lookup (internal) ─────────────────────────────────────────────

    /**
     * Read-only fetch by id. Used by interview-service to verify ownership
     * and READY state before pinning a {@code storageObjectId} to an answer
     * (see {@code AnswerService.verifyStorageObject} on the orchestrator).
     *
     * <p>The full {@link StorageObjectResponse} is returned — the caller
     * decides which fields it needs. {@code ownerUserId} in particular is
     * what closes the cross-user ACL hole an inattentive frontend could
     * otherwise open.
     */
    @Transactional(readOnly = true)
    public StorageObjectResponse getObjectById(String objectId) {
        StorageObject object = storageObjectRepository.findById(objectId)
                .orElseThrow(() -> new BusinessException(StatusCode.STORAGE_OBJECT_NOT_FOUND));
        return StorageObjectResponse.from(object);
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
