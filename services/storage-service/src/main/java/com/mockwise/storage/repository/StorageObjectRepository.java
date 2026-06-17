package com.mockwise.storage.repository;

import com.mockwise.storage.entity.StorageObject;
import com.mockwise.storage.enums.StorageKind;
import com.mockwise.storage.enums.StorageStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface StorageObjectRepository extends JpaRepository<StorageObject, String> {

    Optional<StorageObject> findByBucketAndObjectKey(String bucket, String objectKey);

    /**
     * Stale uploads that never completed — used by {@code PendingUploadReaper}
     * to reclaim orphaned bytes (e.g. a candidate closed the tab mid-upload
     * after a Lever 2 fast-path submit). Bounded by {@code limit} so one sweep
     * can't load an unbounded backlog.
     */
    List<StorageObject> findByStatusAndCreatedAtBefore(
            StorageStatus status, OffsetDateTime cutoff, Limit limit);

    /**
     * Latest READY object of a given kind for a user — used by the avatar
     * streaming endpoint so older orphan uploads aren't returned after a
     * re-upload.
     */
    Optional<StorageObject> findFirstByOwnerUserIdAndKindAndStatusOrderByCompletedAtDesc(
            String ownerUserId, StorageKind kind, StorageStatus status);
}
