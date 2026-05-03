package com.mockwise.interview.entity;

import com.mockwise.interview.entity.enums.AnswerStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit row written every time an answer's status flips.
 * BIGSERIAL id keeps inserts cheap; queries are always by {@code answer_id}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "answer_event_log")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AnswerEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "answer_id", nullable = false)
    UUID answerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    AnswerStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    AnswerStatus toStatus;

    @Column(length = 100)
    String reason;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> metadata = new HashMap<>();

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    OffsetDateTime occurredAt = OffsetDateTime.now();
}
