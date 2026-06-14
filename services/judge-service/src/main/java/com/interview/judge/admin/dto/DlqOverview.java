package com.interview.judge.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Snapshot of the judge dead-letter topic ({@code code-submission.DLT}). These
 * are submissions that could not be processed (e.g. a poison-pill payload that
 * never deserializes) and were routed aside so the consumer keeps progressing.
 *
 * @param topic     the DLT topic name probed
 * @param reachable whether Kafka / the topic could be read
 * @param total     total messages currently retained on the topic
 * @param messages  the most recent messages (capped), newest first
 * @param note      human-readable note when not reachable / empty
 */
public record DlqOverview(
        String topic,
        boolean reachable,
        long total,
        List<DlqMessage> messages,
        String note
) {
    public record DlqMessage(
            int partition,
            long offset,
            OffsetDateTime timestamp,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            String exceptionClass,
            String exceptionMessage,
            String payloadPreview
    ) {}
}
