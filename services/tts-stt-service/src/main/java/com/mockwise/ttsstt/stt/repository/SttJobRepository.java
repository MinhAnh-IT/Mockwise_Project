package com.mockwise.ttsstt.stt.repository;

import com.mockwise.ttsstt.stt.entity.SttJob;
import com.mockwise.ttsstt.stt.entity.SttJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SttJobRepository extends JpaRepository<SttJob, String> {
    Optional<SttJob> findByStorageObjectId(String storageObjectId);

    List<SttJob> findByStatusAndStartedAtBefore(SttJobStatus status, OffsetDateTime cutoff);

    List<SttJob> findByStatusInAndPublishedEventAtIsNull(List<SttJobStatus> statuses);
}
