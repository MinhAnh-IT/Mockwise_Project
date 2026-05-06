package com.mockwise.interview.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox row. The service writes one of these in the same
 * transaction as the business write that triggered it; a background poller
 * then flips {@code published = true} after the Kafka send succeeds.
 *
 * <p>The schema's partial index on {@code (published, created_at)
 * WHERE published = false} keeps the unread queue cheap to scan.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "outbox_event")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent {

    @Id
    @UuidGenerator
    UUID id;

    @Column(nullable = false, length = 100)
    String topic;

    @Column(name = "aggregate_id")
    UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    String eventType;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> payload = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    boolean published = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @Column(name = "published_at")
    OffsetDateTime publishedAt;
}
