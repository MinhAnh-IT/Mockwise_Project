package com.mockwise.storage.entity;

import com.mockwise.storage.enums.StorageKind;
import com.mockwise.storage.enums.StorageStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "storage_object")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StorageObject {

    @Id
    @UuidGenerator
    String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    StorageKind kind;

    @Column(nullable = false, length = 100)
    String bucket;

    @Column(name = "object_key", nullable = false, length = 500)
    String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    String contentType;

    @Column(name = "size_bytes")
    Long sizeBytes;

    @Column(length = 64)
    String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    StorageStatus status;

    @Column(name = "owner_user_id", length = 36)
    String ownerUserId;

    @Column(name = "session_id", length = 36)
    String sessionId;

    @Column(name = "question_id", length = 36)
    String questionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    OffsetDateTime completedAt;
}
