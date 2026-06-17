package com.mockwise.storage.service;

import com.mockwise.storage.entity.StorageObject;
import com.mockwise.storage.enums.StorageStatus;
import com.mockwise.storage.gateway.MinioStorageGateway;
import com.mockwise.storage.repository.StorageObjectRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reclaims abandoned uploads. A {@code PENDING_UPLOAD} row that never reaches
 * READY past a grace window is an orphan — a presigned upload whose PUT/complete
 * never happened, or (more common since the Lever 2 fast path detaches the
 * video upload from submit, realtime-stt-plan.md §8.4 #10) a streaming upload
 * whose tab closed mid-flight, leaving {@code objectKey.part.N} bytes in MinIO.
 *
 * <p>The grace window must comfortably exceed the longest legitimate
 * record-then-upload, so a still-in-progress upload is never swept. Each sweep
 * deletes everything under the object's key prefix (the composed object, if any,
 * plus every part) then the row. Bounded per run; best-effort — a failed MinIO
 * delete leaves a reclaimable orphan for the next sweep rather than throwing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PendingUploadReaper {

    StorageObjectRepository repository;
    MinioStorageGateway minioGateway;

    @Value("${storage.reaper.pending-grace-minutes:120}")
    int graceMinutes;

    @Value("${storage.reaper.batch-size:200}")
    int batchSize;

    /**
     * Sweeps once per interval (default 30 min). Each stale row is reaped in its
     * own transaction so one failure doesn't abort the batch.
     */
    @Scheduled(
            fixedDelayString = "${storage.reaper.interval-ms:1800000}",
            initialDelayString = "${storage.reaper.initial-delay-ms:300000}")
    public void sweep() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(graceMinutes);
        List<StorageObject> stale = repository.findByStatusAndCreatedAtBefore(
                StorageStatus.PENDING_UPLOAD, cutoff, Limit.of(batchSize));
        if (stale.isEmpty()) {
            return;
        }
        int reaped = 0;
        for (StorageObject obj : stale) {
            try {
                reapOne(obj);
                reaped++;
            } catch (Exception e) {
                log.warn("Reaper failed for pending upload {} ({}): {}",
                        obj.getId(), obj.getObjectKey(), e.getMessage());
            }
        }
        log.info("PendingUploadReaper swept {} / {} stale uploads (grace={}m)",
                reaped, stale.size(), graceMinutes);
    }

    void reapOne(StorageObject obj) {
        // Delete the composed object (if compose ever ran) + every streamed part
        // under the same key prefix. listObjectKeys covers both in one pass.
        for (String key : minioGateway.listObjectKeys(obj.getBucket(), obj.getObjectKey())) {
            minioGateway.removeObject(obj.getBucket(), key);
        }
        repository.delete(obj);
        log.debug("Reaped orphan pending upload {} ({}/{})",
                obj.getId(), obj.getBucket(), obj.getObjectKey());
    }
}
