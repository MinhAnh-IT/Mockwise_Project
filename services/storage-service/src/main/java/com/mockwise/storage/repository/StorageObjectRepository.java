package com.mockwise.storage.repository;

import com.mockwise.storage.entity.StorageObject;
import com.mockwise.storage.enums.StorageKind;
import com.mockwise.storage.enums.StorageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorageObjectRepository extends JpaRepository<StorageObject, String> {

    Optional<StorageObject> findByBucketAndObjectKey(String bucket, String objectKey);

    /**
     * Latest READY object of a given kind for a user — used by the avatar
     * streaming endpoint so older orphan uploads aren't returned after a
     * re-upload.
     */
    Optional<StorageObject> findFirstByOwnerUserIdAndKindAndStatusOrderByCompletedAtDesc(
            String ownerUserId, StorageKind kind, StorageStatus status);
}
