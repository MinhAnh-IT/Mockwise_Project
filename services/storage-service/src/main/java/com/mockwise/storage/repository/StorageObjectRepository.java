package com.mockwise.storage.repository;

import com.mockwise.storage.entity.StorageObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorageObjectRepository extends JpaRepository<StorageObject, String> {

    Optional<StorageObject> findByBucketAndObjectKey(String bucket, String objectKey);
}
