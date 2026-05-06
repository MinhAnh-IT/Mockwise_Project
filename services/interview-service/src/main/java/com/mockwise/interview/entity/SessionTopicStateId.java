package com.mockwise.interview.entity;

import com.mockwise.interview.enums.TopicKind;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link SessionTopicState}. Hibernate requires
 * {@code @Embeddable} composite keys to implement {@link Serializable} and
 * a stable {@code equals}/{@code hashCode} — Lombok's {@code @EqualsAndHashCode}
 * provides both.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionTopicStateId implements Serializable {

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_kind", nullable = false, length = 20)
    TopicKind topicKind;

    @Column(name = "topic_value", nullable = false, length = 50)
    String topicValue;
}
