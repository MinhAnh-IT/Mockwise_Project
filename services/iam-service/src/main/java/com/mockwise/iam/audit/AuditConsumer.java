package com.mockwise.iam.audit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mockwise.iam.message.constants.KafkaTopics;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Sole consumer of the {@code audit-log} topic. Parses each JSON event, derives
 * the semantic fields for raw gateway events ({@link AuditClassifier}), and
 * persists one immutable {@link AuditLog} row.
 *
 * <p>A malformed (poison) message is logged and acked, never retried — audit is
 * best-effort and one bad record must not wedge the partition (see the prod
 * poison-message incident). Manual ack mode commits the offset only after the
 * row is saved, so a transient DB failure redelivers rather than loses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditConsumer {

    AuditLogRepository repository;

    static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(topics = KafkaTopics.AUDIT, groupId = "${spring.kafka.consumer.group-id:iam-audit}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        AuditEvent event;
        try {
            event = MAPPER.readValue(record.value(), AuditEvent.class);
        } catch (Exception e) {
            log.error("Skipping poison audit message at offset {}: {}", record.offset(), e.getMessage());
            ack.acknowledge();
            return;
        }

        try {
            repository.save(toEntity(event));
            ack.acknowledge();
        } catch (Exception e) {
            // Don't ack — let Kafka redeliver on the next poll (transient DB issue).
            log.error("Failed to persist audit event (will retry): {}", e.getMessage(), e);
            throw e;
        }
    }

    private AuditLog toEntity(AuditEvent e) {
        // Auth events arrive pre-classified; gateway events are raw → derive here.
        String action = e.getAction();
        String category = e.getCategory();
        String targetType = e.getTargetType();
        String targetId = e.getTargetId();

        if (!StringUtils.hasText(action)) {
            AuditClassifier.Classification c = AuditClassifier.classify(e.getHttpMethod(), e.getPath());
            action = c.action();
            category = c.category();
            targetType = c.targetType();
            targetId = c.targetId();
        }

        String outcome = StringUtils.hasText(e.getOutcome())
                ? e.getOutcome()
                : AuditClassifier.outcomeOf(e.getStatusCode());

        return AuditLog.builder()
                .occurredAt(parseInstant(e.getOccurredAt()))
                .actorId(e.getActorId())
                .actorEmail(e.getActorEmail())
                .actorRole(e.getActorRole())
                .action(action)
                .category(category != null ? category : "CONTENT")
                .targetType(targetType)
                .targetId(targetId)
                .httpMethod(e.getHttpMethod())
                .path(truncate(e.getPath(), 512))
                .statusCode(e.getStatusCode())
                .outcome(outcome)
                .ip(truncate(e.getIp(), 64))
                .userAgent(truncate(e.getUserAgent(), 512))
                .latencyMs(e.getLatencyMs())
                .source(StringUtils.hasText(e.getSource()) ? e.getSource() : "unknown")
                .detail(e.getDetail())
                .build();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** ISO-8601 string → Instant; falls back to now() for a missing or malformed value. */
    private static Instant parseInstant(String s) {
        if (!StringUtils.hasText(s)) return Instant.now();
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
