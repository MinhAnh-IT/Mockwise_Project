package com.mockwise.userprofile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    // Author when the sender is logged in; null for anonymous landing-page
    // visitors (the submit endpoint is public).
    @Column(length = 64)
    String userId;

    @Column(nullable = false)
    Integer rating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    FeedbackCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    String content;

    // Optional contact left by the sender so the admin can follow up.
    @Column(length = 320)
    String contactEmail;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    FeedbackStatus status = FeedbackStatus.NEW;

    // Internal triage note, never shown to the sender.
    @Column(columnDefinition = "TEXT")
    String adminNote;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    Instant createdAt;

    // Stamped the first time an admin moves the item out of NEW.
    Instant reviewedAt;
}
