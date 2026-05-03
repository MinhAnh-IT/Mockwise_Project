package com.mockwise.interview.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

/**
 * Inbound Kafka event dedup row. Inserted in the same transaction as the
 * business effect of an event so a crash between the two cannot leak a
 * duplicate. Settles the §10 design open question by picking the DB-backed
 * option over Redis.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "processed_event")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64)
    String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    String eventType;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    OffsetDateTime processedAt = OffsetDateTime.now();
}
